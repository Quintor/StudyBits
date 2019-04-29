package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class CredentialDefinitionService {
    @Getter
    private String credentialDefinitionId;

    @Getter
    private String schemaId;

    @Autowired
    private Issuer universityIssuer;

    public String createCredentialDefintion(String schemaId) throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        this.schemaId = schemaId;
        credentialDefinitionId = universityIssuer.defineCredential(schemaId).get();
        return credentialDefinitionId;
    }
}
