package nl.quintor.studybits.controller;

import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.service.AgentService;
import nl.quintor.studybits.service.ExchangePositionService;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
    public MessageEnvelope login(@RequestBody String message) throws InterruptedException, ExecutionException, IndyException, IOException {
        return agentService.login(MessageEnvelope.parseFromString(message, IndyMessageTypes.CONNECTION_REQUEST));
    }
}
