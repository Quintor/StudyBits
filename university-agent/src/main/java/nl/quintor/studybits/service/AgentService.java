package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.Verifier;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.*;
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
import static nl.quintor.studybits.messages.StudyBitsMessageTypes.EXCHANGE_POSITIONS;

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

        if (messageTypeURN.equals(GET_REQUEST.getURN())) {
            MessageEnvelope<String> envelopeType = MessageEnvelope.convertEnvelope(messageEnvelope, GET_REQUEST);
            MessageType requestedMessageType = MessageTypes.forURN(messageEnvelopeCodec.decryptMessage(envelopeType).get());

            if(requestedMessageType.equals(CREDENTIAL_OFFERS)) {
                return getCredentialOffers(messageEnvelope.getDid());
            }
            else if(requestedMessageType.equals(EXCHANGE_POSITIONS)) {
                return exchangePositionService.getAll(messageEnvelope.getDid());
            }
        }
        else if (messageTypeURN.equals(CREDENTIAL_REQUEST.getURN())) {
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
        log.debug("STUDENT: " + student);
        ConnectionRequest connectionRequest = messageEnvelopeCodec.decryptMessage(messageEnvelope).get();

        // If there is no student in the login message, create one for future purposes.
        if (student == null) {
            student = studentService.createStudent(null, "", connectionRequest.getDid());
            studentId = student.getStudentId();
        } else { // If a student exists in the login message then login
            if(studentService.matchPassword(password, student.getPassword())) {
                //Check wheter the student already connected with the university before
                if(student.hasDid()) {
//                    #TODO: Design behavior when logging in with same Wallet while lost Pairwise DID
//                    if(student.getStudentDid().equals(messageEnvelope.getDid())) {
//                        //Perhabs overwrite?
//                    } else {
                        //Conflict, student should request a reset
                        throw new AccessDeniedException("Access denied for student " + studentId + ". DID already exists.");
//                    }
                }
            }else { //Login is not valid
                throw new AccessDeniedException("Access denied for student " + studentId +  ". incorrect Student ID and/or Password." );
            }
        }


        ConnectionResponse connectionResponse = universityTrustAnchor.acceptConnectionRequest(connectionRequest).get();
        log.debug("Student DID: " + connectionResponse.getDid());
        log.debug("Student DID2: " + connectionRequest.getDid());
        studentService.setStudentDid(studentId, connectionRequest.getDid());
        return messageEnvelopeCodec.encryptMessage(connectionResponse, IndyMessageTypes.CONNECTION_RESPONSE, connectionRequest.getDid()).get();
    }

    public MessageEnvelope<CredentialOfferList> getCredentialOffers(String did) throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        CredentialOfferList credentialOffers = new CredentialOfferList();
        Student student = studentService.getStudentByStudentDid(did);

        if (student.getTranscript() != null && !student.getTranscript().isProven()) {
            CredentialOffer credentialOffer = universityIssuer.createCredentialOffer(credentialDefinitionService.getCredentialDefinitionId(), did).get();
            credentialOffers.addCredentialOffer(credentialOffer);
            return messageEnvelopeCodec.encryptMessage(credentialOffers, IndyMessageTypes.CREDENTIAL_OFFERS, did).get();
        }

        return messageEnvelopeCodec.encryptMessage(credentialOffers, IndyMessageTypes.CREDENTIAL_OFFERS, did).get();
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

        return messageEnvelopeCodec.encryptMessage(credentialWithRequest, IndyMessageTypes.CREDENTIAL, messageEnvelope.getDid()).get();
    }

    private MessageEnvelope handleProof(MessageEnvelope<Proof> proofEnvelope) throws IndyException, ExecutionException, InterruptedException, IOException {
        log.debug("Handling proof");
        Student student = studentService.getStudentByStudentDid(proofEnvelope.getDid());
        ProofRequest proofRequest = JSONUtil.mapper.readValue(student.getProofRequest(), ProofRequest.class);

        Proof proof = messageEnvelopeCodec.decryptMessage(proofEnvelope).get();
        log.debug("Proof: {}", proof);
        List<ProofAttribute> proofAttributes = universityVerifier.getVerifiedProofAttributes(proofRequest, proof, proofEnvelope.getDid()).get();


        exchangePositionService.fullfillPosition(student.getExchangePosition().getId());
        return null;
    }
}
