package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.dto.AnoncryptedMessage;
import nl.quintor.studybits.indy.wrapper.dto.ConnectionRequest;
import nl.quintor.studybits.indy.wrapper.dto.ConnectionResponse;
import nl.quintor.studybits.indy.wrapper.dto.Serializable;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import org.apache.commons.lang3.NotImplementedException;
import org.aspectj.bridge.Message;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class AgentService {

    @Autowired
    private TrustAnchor trustAnchor;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private StudentService studentService;

    public CompletableFuture<MessageEnvelope> processMessage(MessageEnvelope messageEnvelope) throws JsonProcessingException, IndyException {
        switch (messageEnvelope.getType()) {
            case CONNECTION_RESPONSE:
                return handleConnectionResponse(messageEnvelope);

            default:
                throw new NotImplementedException("Processing of message type not supported: " + messageEnvelope.getType());

        }
    }

    public CompletableFuture<MessageEnvelope> login(String studentId) throws IndyException, ExecutionException, InterruptedException {
        identityService.setStudentId(studentId);
        return trustAnchor.createConnectionRequest(studentId, null)
                .thenApply(connectionRequest -> {
                    studentService.setConnectionData(studentId, connectionRequest.getDid(), connectionRequest.getRequestNonce());

                    return this.unencryptedFromSerializable(connectionRequest.getRequestNonce(),
                            MessageEnvelope.MessageType.CONNECTION_REQUEST, connectionRequest);
                });
    }

    private CompletableFuture<MessageEnvelope> handleConnectionResponse(MessageEnvelope messageEnvelope) throws JsonProcessingException, IndyException {
        AnoncryptedMessage anoncryptedConnectionResponse = fromEnvelope(messageEnvelope);
        return trustAnchor.anonDecrypt(anoncryptedConnectionResponse, ConnectionResponse.class)
                .thenCompose(AsyncUtil.wrapException(trustAnchor::acceptConnectionResponse))
                // TODO proper connection acknowledgement
                .thenApply(result -> new MessageEnvelope("", MessageEnvelope.MessageType.CONNECTION_ACKNOWLEDGEMENT, new TextNode("")));
    }

    private MessageEnvelope unencryptedFromSerializable(String id, MessageEnvelope.MessageType type, Serializable message) {
        return new MessageEnvelope(id, type, JSONUtil.mapper.valueToTree(message));
    }

    private AnoncryptedMessage fromEnvelope(MessageEnvelope messageEnvelope) {
        byte[] decodedBytes = Base64.getDecoder().decode(messageEnvelope.getMessage().asText());
        return new AnoncryptedMessage(decodedBytes, studentService.getMyDidByRequestNonce(messageEnvelope.getId()));
    }

}
