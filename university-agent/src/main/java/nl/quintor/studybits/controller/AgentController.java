package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.quintor.studybits.indy.wrapper.dto.CredentialOfferList;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.messages.AuthcryptableExchangePositions;
import nl.quintor.studybits.service.AgentService;
import nl.quintor.studybits.service.ExchangePositionService;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(value = "/agent", produces = "application/json")
public class AgentController {
    @Autowired
    private AgentService agentService;

    @Autowired
    private ExchangePositionService exchangePositionService;

    @PostMapping("/message")
    public MessageEnvelope processMessage(@RequestBody String message) throws IOException, IndyException, ExecutionException, InterruptedException {
        return agentService.processMessage(MessageEnvelope.parseFromString(message));
    }

    @PostMapping("/login")
    public MessageEnvelope login(@RequestParam(value = "student_id", required = false) String studentId, @RequestParam(value = "password", required = false) String password, @RequestBody String message) throws InterruptedException, ExecutionException, IndyException, IOException {
        return agentService.login(studentId, password, MessageEnvelope.parseFromString(message, IndyMessageTypes.CONNECTION_REQUEST));
    }

//    @PostMapping("/credential_offer")
//    public MessageEnvelope<CredentialOfferList> credentialOffers(@RequestBody String message) throws ExecutionException, InterruptedException, IOException, IndyException {
//        return agentService.getCredentialOffers(MessageEnvelope.parseFromString(message, IndyMessageTypes.GET_REQUEST).getDid());
//    }

//    @GetMapping("/exchange_position")
//    public MessageEnvelope<AuthcryptableExchangePositions> exchangePositions() throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
//        return exchangePositionService.getAll();
//    }
}
