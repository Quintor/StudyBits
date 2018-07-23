package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.bytebuddy.asm.Advice;
import nl.quintor.studybits.Seeder;
import nl.quintor.studybits.entity.ExchangePosition;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.dto.AttributeInfo;
import nl.quintor.studybits.indy.wrapper.dto.Filter;
import nl.quintor.studybits.indy.wrapper.dto.PredicateInfo;
import nl.quintor.studybits.indy.wrapper.dto.ProofRequest;
import nl.quintor.studybits.repository.ExchangePositionRepository;
import nl.quintor.studybits.repository.StudentRepository;
import nl.quintor.studybits.service.CredentialDefinitionService;
import nl.quintor.studybits.service.ExchangePositionService;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.CredDefAlreadyExistsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(value = "/bootstrap", produces = "application/json")
public class BootstrapController {
    @Autowired
    private CredentialDefinitionService credentialDefinitionService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ExchangePositionService exchangePositionService;

    @Autowired
    private Seeder seeder;

    @PostMapping("/credential_definition/{schemaId}")
    public void createCredentialDefinition(@PathVariable("schemaId") String schemaId) throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        try {
            credentialDefinitionService.createCredentialDefintion(schemaId);
        }
        catch (ExecutionException e) {
            // This is totally fine
            if (!(e.getCause() instanceof CredDefAlreadyExistsException)) {
                throw e;
            }
        }
    }

    @PostMapping("/exchange_position/{credDefId}")
    public void createExchangePosition(@PathVariable("credDefId") String credDefId) throws JsonProcessingException {
        exchangePositionService.createExchangePosition(credDefId);
    }

    @PostMapping("/reset")
    public void reset(){
        studentRepository.deleteAll();
        seeder.seed();
    }
}
