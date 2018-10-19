package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.Verifier;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import org.apache.commons.lang3.NotImplementedException;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.*;

@Service
@Slf4j
public class AgentService {

    private final TrustAnchor universityTrustAnchor;

    private final Issuer universityIssuer;

    private final Verifier universityVerifier;

    private final IdentityService identityService;

    private final StudentService studentService;

    private final CredentialDefinitionService credentialDefinitionService;

    private final ExchangePositionService exchangePositionService;

    private final MessageEnvelopeCodec messageEnvelopeCodec;

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    @Autowired
    public AgentService(TrustAnchor universityTrustAnchor, Issuer universityIssuer, Verifier universityVerifier, IdentityService identityService, StudentService studentService, CredentialDefinitionService credentialDefinitionService, ExchangePositionService exchangePositionService) {
        this.universityTrustAnchor = universityTrustAnchor;
        this.universityIssuer = universityIssuer;
        this.universityVerifier = universityVerifier;
        this.identityService = identityService;
        this.studentService = studentService;
        this.credentialDefinitionService = credentialDefinitionService;
        this.exchangePositionService = exchangePositionService;
        this.messageEnvelopeCodec = new MessageEnvelopeCodec(universityIssuer);
    }


    public MessageEnvelope processMessage(MessageEnvelope messageEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        String messageTypeURN = messageEnvelope.getMessageType().getURN();


        if (messageTypeURN.equals(CONNECTION_RESPONSE.getURN())) {
            return handleConnectionResponse(MessageEnvelope.convertEnvelope(messageEnvelope, CONNECTION_RESPONSE));
        }
        else if (messageTypeURN.equals(CREDENTIAL_REQUEST.getURN())) {
            return handleCredentialRequest(messageEnvelope);
        }
        else if (messageTypeURN.equals(PROOF.getURN())) {
            return handleProof(messageEnvelope);
        }

        throw new NotImplementedException("Processing of message type not supported: " + messageEnvelope.getMessageType());
    }

    public MessageEnvelope login(String studentId) throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        Student student = studentService.getStudentByStudentId(studentId);

        if (student == null) {
            student = studentService.createStudent();
            studentId = student.getStudentId();
        }

        log.debug("Setting studentId in identityService to {}", studentId);
        identityService.setStudentId(studentId);
        ConnectionRequest connectionRequest = universityTrustAnchor.createConnectionRequest(studentId, null).get();

        studentService.setConnectionData(studentId, connectionRequest.getDid(), connectionRequest.getRequestNonce());

        return messageEnvelopeCodec.encryptMessage(connectionRequest, IndyMessageTypes.CONNECTION_REQUEST).get();
    }

    public List<MessageEnvelope> getCredentialOffers() throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
        if (student != null && student.getTranscript() != null && !student.getTranscript().isProven()) {
            CredentialOffer credentialOffer = universityIssuer.createCredentialOffer(credentialDefinitionService.getCredentialDefinitionId(), student.getStudentDid()).get();
            return Collections.singletonList(messageEnvelopeCodec.encryptMessage(credentialOffer, CREDENTIAL_OFFER).get());
        }

        return Collections.emptyList();
    }

    private MessageEnvelope handleConnectionResponse(MessageEnvelope<ConnectionResponse> messageEnvelope) throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        ConnectionResponse connectionResponse = messageEnvelopeCodec.decryptMessage(messageEnvelope).get();

        studentService.setStudentDid(identityService.getStudentId(), connectionResponse.getDid());
        String studentUniversityDid = universityTrustAnchor.acceptConnectionResponse(connectionResponse).get();

        log.debug("studentUniversityDid: "  + studentUniversityDid);

        log.debug("Acknowledging with name: {}", universityName);

        return messageEnvelopeCodec.encryptMessage(new AuthcryptableString(universityName, connectionResponse.getDid()), CONNECTION_ACKNOWLEDGEMENT).get();
    }

    private MessageEnvelope handleCredentialRequest(MessageEnvelope<CredentialRequest> messageEnvelope) throws IndyException, ExecutionException, InterruptedException, UnsupportedEncodingException, JsonProcessingException {
        CredentialRequest credentialRequest = messageEnvelopeCodec.decryptMessage(messageEnvelope).get();
        log.debug("Decrypted request");
        Student student = studentService.getStudentByStudentDid(messageEnvelope.getDidOrNonce());

        Map<String, Object> values = new HashMap<>();
        values.put("first_name", student.getFirstName());
        values.put("last_name", student.getLastName());
        values.put("degree", student.getTranscript().getDegree());
        values.put("average", student.getTranscript().getAverage());
        values.put("status", student.getTranscript().getStatus());

        CredentialWithRequest credentialWithRequest = universityIssuer.createCredential(credentialRequest, values).get();

        studentService.proveTranscript(student.getStudentId());

        return messageEnvelopeCodec.encryptMessage(credentialWithRequest, CREDENTIAL).get();
    }

    private MessageEnvelope handleProof(MessageEnvelope<Proof> proofEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
        ProofRequest proofRequest = JSONUtil.mapper.readValue(student.getProofRequest(), ProofRequest.class);

        Proof proof = messageEnvelopeCodec.decryptMessage(proofEnvelope).get();

        List<ProofAttribute> proofAttributes = universityVerifier.getVerifiedProofAttributes(proofRequest, proof).get();


        exchangePositionService.fullfillPosition(student.getExchangePosition().getId());
        return null;
    }
}
