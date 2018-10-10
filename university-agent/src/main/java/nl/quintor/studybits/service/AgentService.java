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
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageType;
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

import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.*;

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
        String messageTypeURN = messageEnvelope.getType().getURN();

        messageEnvelope.setIndyWallet(universityIssuer);

        if (messageTypeURN.equals(CONNECTION_RESPONSE.getURN())) {
            return handleConnectionResponse(messageEnvelope);
        }
        else if (messageTypeURN.equals(CREDENTIAL_REQUEST.getURN())) {
            return handleCredentialRequest(messageEnvelope);
        }
        else if (messageTypeURN.equals(PROOF.getURN())) {
            return handleProof(messageEnvelope);
        }

        throw new NotImplementedException("Processing of message type not supported: " + messageEnvelope.getType());
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

        return new MessageEnvelope<>(IndyMessageTypes.CONNECTION_REQUEST, connectionRequest, null, universityTrustAnchor, null);
    }

    public List<MessageEnvelope> getCredentialOffers() throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
        if (student != null && student.getTranscript() != null && !student.getTranscript().isProven()) {
            CredentialOffer credentialOffer = universityIssuer.createCredentialOffer(credentialDefinitionService.getCredentialDefinitionId(), student.getStudentDid()).get();
            return Collections.singletonList(MessageEnvelope.fromAuthcryptable(credentialOffer, CREDENTIAL_OFFER, universityIssuer));
        }

        return Collections.emptyList();
    }

    private MessageEnvelope handleConnectionResponse(MessageEnvelope<ConnectionResponse> messageEnvelope) throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        ConnectionResponse connectionResponse = messageEnvelope.getMessage();

        studentService.setStudentDid(identityService.getStudentId(), connectionResponse.getDid());
        String studentUniversityDid = universityTrustAnchor.acceptConnectionResponse(connectionResponse).get();
        //Dit is de student DID?
        System.out.println("studentUniversityDid: "  + studentUniversityDid);

        log.debug("Acknowledging with name: {}", universityName);
        // TODO proper connection acknowledgement
        return new MessageEnvelope<>(CONNECTION_ACKNOWLEDGEMENT, universityName, null, universityIssuer, connectionResponse.getDid());
    }

    private MessageEnvelope handleCredentialRequest(MessageEnvelope<CredentialRequest> messageEnvelope) throws IndyException, ExecutionException, InterruptedException, UnsupportedEncodingException, JsonProcessingException {
        CredentialRequest credentialRequest = messageEnvelope.getMessage();
        log.debug("Decrypted request");
        Student student = studentService.getStudentByStudentDid(messageEnvelope.getDid());

        Map<String, Object> values = new HashMap<>();
        values.put("first_name", student.getFirstName());
        values.put("last_name", student.getLastName());
        values.put("degree", student.getTranscript().getDegree());
        values.put("average", student.getTranscript().getAverage());
        values.put("status", student.getTranscript().getStatus());

        CredentialWithRequest credentialWithRequest = universityIssuer.createCredential(credentialRequest, values).get();

        studentService.proveTranscript(student.getStudentId());

        return MessageEnvelope.fromAuthcryptable(credentialWithRequest, CREDENTIAL, universityIssuer);
    }

    private MessageEnvelope handleProof(MessageEnvelope<Proof> proofEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
        ProofRequest proofRequest = JSONUtil.mapper.readValue(student.getProofRequest(), ProofRequest.class);

        Proof proof = proofEnvelope.getMessage();

        List<ProofAttribute> proofAttributes = universityVerifier.getVerifiedProofAttributes(proofRequest, proof).get();


        exchangePositionService.fullfillPosition(student.getExchangePosition().getId());
        return null;
    }
}
