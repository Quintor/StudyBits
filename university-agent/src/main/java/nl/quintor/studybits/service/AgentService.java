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

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
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
    private StudentService studentService;
    @Autowired
    private CredentialDefinitionService credentialDefinitionService;
    @Autowired
    private ExchangePositionService exchangePositionService;
    @Autowired
    private MessageEnvelopeCodec messageEnvelopeCodec;

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    public MessageEnvelope processMessage(MessageEnvelope messageEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        String messageTypeURN = messageEnvelope.getMessageType().getURN();


        if (messageTypeURN.equals(CREDENTIAL_REQUEST.getURN())) {
            return handleCredentialRequest(MessageEnvelope.convertEnvelope(messageEnvelope, CREDENTIAL_REQUEST));
        }
        else if (messageTypeURN.equals(PROOF.getURN())) {
            return handleProof(MessageEnvelope.convertEnvelope(messageEnvelope, PROOF));
        }

        throw new NotImplementedException("Processing of message type not supported: " + messageEnvelope.getMessageType());
    }

    // Student sets up a connection with university agent
    public MessageEnvelope<ConnectionResponse> login(String studentId, String password, MessageEnvelope<ConnectionRequest> messageEnvelope) throws IndyException, ExecutionException, InterruptedException, JsonProcessingException, AccessDeniedException {
        Student student = studentService.getStudentByStudentId(studentId);

        log.debug("StudentID: "+ studentId);

        ConnectionRequest connectionRequest = messageEnvelopeCodec.decryptMessage(messageEnvelope).get();

        // If there is no student the login message, create one for future purposes.
        if (student == null) {
            student = studentService.createStudent(null, "", connectionRequest.getDid());
            studentId = student.getStudentId();
        } else { // If a student exists in the login message then login
            if(studentService.matchPassword(password, student.getPassword())) {
                //Check wheter the student already connected with the university before
                if(student.hasDid()) {
                    //Conflict, student should request a reset
                    throw new AccessDeniedException("Access denied for student " + studentId + ". DID already exists.");
                } else {
                    //Student applied new DID
                    studentService.setStudentDid(studentId, connectionRequest.getDid());
                }
            }else { //Login is not valid
                throw new AccessDeniedException("Access denied for student " + studentId +  ". incorrect Student ID or Password." );
            }



            //Check wheter the student already connected with the university before
//            if(student.hasDid()){
//                // Match the student login and DID
//                if(studentService.matchPassword(password, student.getPassword()) && connectionRequest.getDid().equals(student.getStudentDid())) {
//                    //Acknowledged
//                } else { //Login or DID not valid
//                    throw new AccessDeniedException("Access denied for student " + studentId);
//                }
//            } else {
//                if(studentService.matchPassword(password, student.getPassword())) {
//                    //If there is no DID set yet then set one
//                    studentService.setStudentDid(studentId, connectionRequest.getDid());
//                } else { //Login is not valid
//                    throw new AccessDeniedException("Access denied for student " + studentId);
//                }
//            }
        }


        ConnectionResponse connectionResponse = universityTrustAnchor.acceptConnectionRequest(connectionRequest).get();
        studentService.setConnectionData(studentId, connectionResponse.getDid());

        return messageEnvelopeCodec.encryptMessage(connectionResponse, IndyMessageTypes.CONNECTION_RESPONSE).get();
    }

    public MessageEnvelope<CredentialOfferList> getCredentialOffers() throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        CredentialOfferList credentialOffers = new CredentialOfferList();

//        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
//        credentialOffers.setTheirDid(student.getStudentDid());

//        if (student.getTranscript() != null && !student.getTranscript().isProven()) {
//            CredentialOffer credentialOffer = universityIssuer.createCredentialOffer(credentialDefinitionService.getCredentialDefinitionId(), student.getStudentDid()).get();
//            credentialOffers.addCredentialOffer(credentialOffer);
//            return messageEnvelopeCodec.encryptMessage(credentialOffers, IndyMessageTypes.CREDENTIAL_OFFERS).get();
//        }

        return messageEnvelopeCodec.encryptMessage(credentialOffers, IndyMessageTypes.CREDENTIAL_OFFERS).get();
    }

    private MessageEnvelope handleCredentialRequest(MessageEnvelope<CredentialRequest> messageEnvelope) throws IndyException, ExecutionException, InterruptedException, UnsupportedEncodingException, JsonProcessingException {
        CredentialRequest credentialRequest = messageEnvelopeCodec.decryptMessage(messageEnvelope).get();
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

        return messageEnvelopeCodec.encryptMessage(credentialWithRequest, IndyMessageTypes.CREDENTIAL).get();
    }

    private MessageEnvelope handleProof(MessageEnvelope<Proof> proofEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        log.debug("Handling proof");
//        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
//        ProofRequest proofRequest = JSONUtil.mapper.readValue(student.getProofRequest(), ProofRequest.class);
//
//        Proof proof = messageEnvelopeCodec.decryptMessage(proofEnvelope).get();
//        log.debug("Proof: {}", proof);
//        List<ProofAttribute> proofAttributes = universityVerifier.getVerifiedProofAttributes(proofRequest, proof).get();
//
//
//        exchangePositionService.fullfillPosition(student.getExchangePosition().getId());
        return null;
    }
}
