package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import org.apache.commons.lang3.NotImplementedException;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
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
    private IdentityService identityService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CredentialDefinitionService credentialDefinitionService;

    public MessageEnvelope processMessage(MessageEnvelope messageEnvelope) throws IndyException, ExecutionException, InterruptedException {
        switch (messageEnvelope.getType()) {
            case CONNECTION_RESPONSE:
                return handleConnectionResponse(messageEnvelope);

            default:
                throw new NotImplementedException("Processing of message type not supported: " + messageEnvelope.getType());

        }
    }

    public MessageEnvelope login(String studentId) throws IndyException, ExecutionException, InterruptedException {
        identityService.setStudentId(studentId);
        ConnectionRequest connectionRequest = universityTrustAnchor.createConnectionRequest(studentId, null).get();

        studentService.setConnectionData(studentId, connectionRequest.getDid(), connectionRequest.getRequestNonce());

        return this.unencryptedFromSerializable(connectionRequest.getRequestNonce(),
                            MessageEnvelope.MessageType.CONNECTION_REQUEST, connectionRequest);

    }

    public List<MessageEnvelope> getCredentialOffers() throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
        if (student.getTranscript() != null && !student.getTranscript().isProven()) {


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

        // TODO proper connection acknowledgement
        return new MessageEnvelope("", MessageEnvelope.MessageType.CONNECTION_ACKNOWLEDGEMENT, new TextNode(""));
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

}
