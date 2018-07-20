package nl.quintor.studybits;

import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.*;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import nl.quintor.studybits.repository.StudentRepository;
import nl.quintor.studybits.service.CredentialDefinitionService;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Component
@Profile("mobile-test")
@Slf4j
public class LedgerSeeder {
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CredentialDefinitionService credentialDefinitionService;

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    private boolean done = false;

    @EventListener
    public void seed(ContextRefreshedEvent event) throws InterruptedException, ExecutionException, IndyException, IOException {
        if (needsSeeding()) {
            String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
            IndyPool indyPool = new IndyPool(poolName);
            IndyWallet stewardWallet = IndyWallet.create(indyPool, "steward_wallet" + System.currentTimeMillis(), "000000000000000000000000Steward1");
            TrustAnchor steward = new TrustAnchor(stewardWallet);

            Issuer university = new Issuer(IndyWallet.create(indyPool, "university_wallet" + System.currentTimeMillis(),
                    StringUtils.leftPad(universityName.replace(" ", ""), 32, '0')));

            // Connecting newcomer with Steward
            String governmentConnectionRequest = steward.createConnectionRequest(university.getName(), "TRUST_ANCHOR").get().toJSON();

            AnoncryptedMessage newcomerConnectionResponse = university.acceptConnectionRequest(JSONUtil.mapper.readValue(governmentConnectionRequest, ConnectionRequest.class))
                    .thenCompose(AsyncUtil.wrapException(university::anonEncrypt))
                    .get();

            steward.anonDecrypt(newcomerConnectionResponse, ConnectionResponse.class)
                    .thenCompose(AsyncUtil.wrapException(steward::acceptConnectionResponse)).get();

            // Create verinym
            AuthcryptedMessage verinym = university.authEncrypt(university.createVerinymRequest(JSONUtil.mapper.readValue(governmentConnectionRequest, ConnectionRequest.class)
                    .getDid()))
                    .get();

            steward.authDecrypt(verinym, Verinym.class)
                    .thenCompose(AsyncUtil.wrapException(steward::acceptVerinymRequest)).get();


            Issuer stewardIssuer = new Issuer(stewardWallet);
            if (universityName.equals("Rijksuniversiteit Groningen")) {
                String schemaId = stewardIssuer.createAndSendSchema("Transcript", "1.0", "degree", "status", "average").get();

                credentialDefinitionService.createCredentialDefintion(schemaId);

                RestTemplate restTemplate = new RestTemplate();

                ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8081/bootstrap/credential_definition/" + schemaId, null, String.class);
            }




            done = true;
            log.info("Finished seeding ledger");
        }
    }

    private boolean needsSeeding() {
        return !done;
    }
}
