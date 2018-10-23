package nl.quintor.studybits;

import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.*;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import nl.quintor.studybits.repository.StudentRepository;
import nl.quintor.studybits.service.CredentialDefinitionService;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.CredDefAlreadyExistsException;
import org.hyperledger.indy.sdk.ledger.LedgerInvalidTransactionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.hyperledger.indy.sdk.pool.Pool;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_REQUEST;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_RESPONSE;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.VERINYM;

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
            Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();
            String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
            IndyPool indyPool = new IndyPool(poolName);
            IndyWallet stewardWallet = IndyWallet.create(indyPool, "steward" + System.currentTimeMillis(), "000000000000000000000000Steward1");
            TrustAnchor steward = new TrustAnchor(stewardWallet);

            Issuer university = new Issuer(IndyWallet.create(indyPool, "university" + System.currentTimeMillis(),
                    StringUtils.leftPad(universityName.replace(" ", ""), 32, '0')));


            onboardIssuer(steward, university);



            Issuer stewardIssuer = new Issuer(stewardWallet);
            if (universityName.equals("Rijksuniversiteit Groningen")) {
                String schemaId = stewardIssuer.createAndSendSchema("Transcript", "1.0", "first_name", "last_name", "degree", "status", "average").get();

                credentialDefinitionService.createCredentialDefintion(schemaId);

                RestTemplate restTemplate = new RestTemplate();

                ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8081/bootstrap/credential_definition/" + schemaId, null, String.class);
                response = restTemplate.postForEntity("http://localhost:8081/bootstrap/exchange_position/" + credentialDefinitionService.getCredentialDefinitionId(), null, String.class);
            }




            done = true;
            log.info("Finished seeding ledger");
        }
    }

    public static void onboardIssuer(TrustAnchor steward, Issuer newcomer) throws InterruptedException, ExecutionException, IndyException, IOException {
        // Create Codecs to facilitate encryption/decryption
        MessageEnvelopeCodec stewardCodec = new MessageEnvelopeCodec(steward);
        MessageEnvelopeCodec newcomerCodec = new MessageEnvelopeCodec(newcomer);

        // Connecting newcomer with Steward

        // Create new DID (For steward_newcomer connection) and send NYM request to ledger
        String governmentConnectionRequest = new MessageEnvelopeCodec(null).encryptMessage(steward.createConnectionRequest(newcomer.getName(), "TRUST_ANCHOR").get(),
                IndyMessageTypes.CONNECTION_REQUEST).get().toJSON();

        // Steward sends connection request to newcomer
        ConnectionRequest connectionRequest = newcomerCodec.decryptMessage(MessageEnvelope.parseFromString(governmentConnectionRequest, CONNECTION_REQUEST)).get();

        // newcomer accepts the connection request from Steward
        ConnectionResponse newcomerConnectionResponse = newcomer.acceptConnectionRequest(connectionRequest).get();

        // newcomer creates a connection response with its created DID and Nonce from the received request from Steward
        String newcomerConnectionResponseString =  newcomerCodec.encryptMessage(newcomerConnectionResponse, IndyMessageTypes.CONNECTION_RESPONSE).get().toJSON();

        // Steward decrypts the anonymously encrypted message from newcomer
        ConnectionResponse connectionResponse = stewardCodec.decryptMessage(MessageEnvelope.parseFromString(newcomerConnectionResponseString, CONNECTION_RESPONSE)).get();

        // Steward authenticates newcomer
        // Steward sends the NYM Transaction for newcomer's DID to the ledger
        steward.acceptConnectionResponse(connectionResponse).get();

        // newcomer needs a new DID to interact with identiy owners, thus create a new DID request steward to write on ledger
        String verinymRequest = newcomerCodec.encryptMessage(newcomer.createVerinymRequest(newcomerCodec.decryptMessage(MessageEnvelope.parseFromString(governmentConnectionRequest, CONNECTION_REQUEST)).get()
                .getDid()), IndyMessageTypes.VERINYM).get().toJSON();

        // Steward accepts verinym request from newcomer and thus writes the new DID on the ledger
        steward.acceptVerinymRequest(stewardCodec.decryptMessage(MessageEnvelope.parseFromString(verinymRequest, VERINYM)).get()).get();
    }

    public boolean needsSeeding() {
        return !done;
    }
}
