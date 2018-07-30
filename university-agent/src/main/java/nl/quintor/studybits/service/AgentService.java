package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.entity.ExchangePosition;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.Verifier;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import org.apache.commons.lang3.NotImplementedException;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class AgentService {

    @Autowired
    private TrustAnchor universityTrustAnchor;

    @Autowired
    private Issuer universityIssuer;

    @Autowired
    private Verifier universityVerifier;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CredentialDefinitionService credentialDefinitionService;

    @Autowired
    private ExchangePositionService exchangePositionService;

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    public MessageEnvelope processMessage(MessageEnvelope messageEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        switch (messageEnvelope.getType()) {
            case CONNECTION_RESPONSE:
                return handleConnectionResponse(messageEnvelope);
            case CREDENTIAL_REQUEST:
                return handleCredentialRequest(messageEnvelope);
            case PROOF:
                return handleProof(messageEnvelope);
            default:
                throw new NotImplementedException("Processing of message type not supported: " + messageEnvelope.getType());

        }
    }

    public MessageEnvelope login(String studentId) throws IndyException, ExecutionException, InterruptedException {
        Student student = studentService.getStudentByStudentId(studentId);

        if (student == null) {
            student = studentService.createStudent();
            studentId = student.getStudentId();
        }

        log.debug("Setting studentId in identityService to {}", studentId);
        identityService.setStudentId(studentId);
        ConnectionRequest connectionRequest = universityTrustAnchor.createConnectionRequest(studentId, null).get();

        studentService.setConnectionData(studentId, connectionRequest.getDid(), connectionRequest.getRequestNonce());

        return this.unencryptedFromSerializable(connectionRequest.getRequestNonce(),
                            MessageEnvelope.MessageType.CONNECTION_REQUEST, connectionRequest);
    }

    public List<MessageEnvelope> getCredentialOffers() throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
        if (student != null && student.getTranscript() != null && !student.getTranscript().isProven()) {
            CredentialOffer credentialOffer = universityIssuer.createCredentialOffer(credentialDefinitionService.getCredentialDefinitionId(), student.getStudentDid()).get();
            AuthcryptedMessage authcryptedMessage = universityIssuer.authEncrypt(credentialOffer).get();
            return Collections.singletonList(this.fromEncrypted(MessageEnvelope.MessageType.CREDENTIAL_OFFER, authcryptedMessage));
        }

        return Collections.emptyList();
    }

    private MessageEnvelope handleConnectionResponse(MessageEnvelope messageEnvelope) throws IndyException, ExecutionException, InterruptedException {
        AnoncryptedMessage anoncryptedConnectionResponse = fromEnvelope(messageEnvelope);
        ConnectionResponse connectionResponse = universityTrustAnchor.anonDecrypt(anoncryptedConnectionResponse, ConnectionResponse.class).get();

        studentService.setStudentDid(identityService.getStudentId(), connectionResponse.getDid());
        universityTrustAnchor.acceptConnectionResponse(connectionResponse).get();

        log.debug("Acknowledging with name: {}", universityName);
        // TODO proper connection acknowledgement
        return new MessageEnvelope("", MessageEnvelope.MessageType.CONNECTION_ACKNOWLEDGEMENT, new TextNode(universityName));
    }

    private MessageEnvelope handleCredentialRequest(MessageEnvelope messageEnvelope) throws IndyException, ExecutionException, InterruptedException, UnsupportedEncodingException, JsonProcessingException {
        AuthcryptedMessage authcryptedCredentialRequest = authcryptedFromEnvelope(messageEnvelope);
        log.debug("Transformed envelope");
        CredentialRequest credentialRequest = universityTrustAnchor.authDecrypt(authcryptedCredentialRequest, CredentialRequest.class).get();
        log.debug("Decrypted request");
        Student student = studentService.getStudentByStudentDid(messageEnvelope.getId());

        Map<String, Object> values = new HashMap<>();
        values.put("first_name", student.getFirstName());
        values.put("last_name", student.getLastName());
        values.put("degree", student.getTranscript().getDegree());
        values.put("average", student.getTranscript().getAverage());
        values.put("status", student.getTranscript().getStatus());

        AuthcryptedMessage authcryptedCredentialWithRequest = universityIssuer.createCredential(credentialRequest, values)
                .thenCompose(AsyncUtil.wrapException(universityIssuer::authEncrypt)).get();

        studentService.proveTranscript(student.getStudentId());

        return fromEncrypted(MessageEnvelope.MessageType.CREDENTIAL, authcryptedCredentialWithRequest);
    }

    private MessageEnvelope handleProof(MessageEnvelope proofEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        AuthcryptedMessage authcryptedProof = authcryptedFromEnvelope(proofEnvelope);
        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
        ProofRequest proofRequest = JSONUtil.mapper.readValue(student.getProofRequest(), ProofRequest.class);


        List<ProofAttribute> proofAttributes = universityIssuer.authDecrypt(authcryptedProof, Proof.class)
                .thenCompose(proof -> universityVerifier.getVerifiedProofAttributes(proofRequest, proof)).get();


        exchangePositionService.fullfillPosition(student.getExchangePosition().getId());
        return new MessageEnvelope();
    }

    private MessageEnvelope unencryptedFromSerializable(String id, MessageEnvelope.MessageType type, Serializable message) {
        return new MessageEnvelope(id, type, JSONUtil.mapper.valueToTree(message));
    }

    private MessageEnvelope fromEncrypted(MessageEnvelope.MessageType type, AuthcryptedMessage message) {
        return new MessageEnvelope(message.getDid(), type, new TextNode(Base64.getEncoder().encodeToString(message.getMessage())));
    }

    private AnoncryptedMessage fromEnvelope(MessageEnvelope messageEnvelope) {
        byte[] decodedBytes = Base64.getDecoder().decode(messageEnvelope.getMessage().asText());
        return new AnoncryptedMessage(decodedBytes, studentService.getMyDidByRequestNonce(messageEnvelope.getId()));
    }

    private AuthcryptedMessage authcryptedFromEnvelope(MessageEnvelope messageEnvelope) {
        byte[] decodedBytes = Base64.getDecoder().decode(messageEnvelope.getMessage().asText());
        return new AuthcryptedMessage(decodedBytes, messageEnvelope.getId());
    }

}
