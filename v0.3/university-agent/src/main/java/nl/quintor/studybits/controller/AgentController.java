package nl.quintor.studybits.controller;

import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.service.AgentService;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(value = "/agent", produces = "application/json")
public class AgentController {
    @Autowired
    private AgentService agentService;

    @PostMapping("/message")
    public MessageEnvelope processMessage(@RequestBody String message) throws IOException, IndyException, ExecutionException, InterruptedException {
        return agentService.processMessage(JSONUtil.mapper.readValue(message, MessageEnvelope.class)).get();
    }

    @PostMapping("/login/{student_id}")
    public MessageEnvelope login(@PathVariable("student_id") String studentId) throws InterruptedException, ExecutionException, IndyException {
        return agentService.login(studentId).get();
    }
}
