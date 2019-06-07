package nl.dionrats.demo.controllers;

import nl.dionrats.demo.services.AgentService;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping(value = "/agent", produces = "application/json")
public class AgentInterceptController {

    @Autowired
    private AgentService agentService;

    @PostMapping("/login")
    public MessageEnvelope login(@RequestBody String message) {
        return agentService.login(message);
    }

}
