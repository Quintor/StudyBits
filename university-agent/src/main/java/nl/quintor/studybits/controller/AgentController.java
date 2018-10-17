package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.quintor.studybits.LedgerSeeder;
import nl.quintor.studybits.indy.wrapper.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.messages.AuthcryptableExchangePositions;
import nl.quintor.studybits.service.AgentService;
import nl.quintor.studybits.service.ExchangePositionService;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @Autowired
    private LedgerSeeder ledgerSeeder;

    @PostMapping("/message")
    public MessageEnvelope processMessage(@RequestBody String message) throws IOException, IndyException, ExecutionException, InterruptedException {
        return agentService.processMessage(MessageEnvelope.parseFromString(message));
    }

    @PostMapping("/login")
    public MessageEnvelope login(@RequestParam(value = "student_id", required = false) String studentId) throws InterruptedException, ExecutionException, IndyException, JsonProcessingException {
        return agentService.login(studentId);
    }

    @GetMapping("/credential_offer")
    public List<MessageEnvelope> credentialOffers() throws ExecutionException, InterruptedException, JsonProcessingException, IndyException {
        return agentService.getCredentialOffers();
    }

    @GetMapping("/exchange_position")
    public MessageEnvelope<AuthcryptableExchangePositions> exchangePositions() throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        return exchangePositionService.getAll();
    }

}
