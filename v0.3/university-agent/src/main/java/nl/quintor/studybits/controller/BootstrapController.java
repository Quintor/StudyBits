package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.service.CredentialDefinitionService;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.CredDefAlreadyExistsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(value = "/bootstrap", produces = "application/json")
public class BootstrapController {
    @Autowired
    private CredentialDefinitionService credentialDefinitionService;

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
}
