package nl.quintor.studybits;

import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.*;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
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

            log.info("Initialized university with did {}", university.getMainDid());



            Issuer stewardIssuer = new Issuer(stewardWallet);
            if (universityName.equals("Rijksuniversiteit Groningen")) {
                String schemaId = stewardIssuer.createAndSendSchema("Transcript", "1.0", "first_name", "last_name", "degree", "status", "average").get();

                credentialDefinitionService.createCredentialDefintion(schemaId);

                RestTemplate restTemplate = new RestTemplate();

//                ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8081/bootstrap/credential_definition/" + schemaId, null, String.class);
//                response = restTemplate.postForEntity("http://localhost:8081/bootstrap/exchange_position/" + credentialDefinitionService.getCredentialDefinitionId(), null, String.class);
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

        // We revert the order from the tutorial, since we use the anoncryption from the verinym

        // Create connection request for steward
        String connectionRequestString = newcomerCodec.encryptMessage(newcomer.createConnectionRequest(steward.getMainDid()).get(),
                IndyMessageTypes.CONNECTION_REQUEST).get().toJSON();

        // Steward decrypts connection request
        ConnectionRequest connectionRequest = stewardCodec.decryptMessage(MessageEnvelope.parseFromString(connectionRequestString, CONNECTION_REQUEST)).get();

        // Steward accepts connection request
        ConnectionResponse newcomerConnectionResponse = steward.acceptConnectionRequest(connectionRequest).get();

        // Steward sends a connection response
        String newcomerConnectionResponseString =  stewardCodec.encryptMessage(newcomerConnectionResponse, IndyMessageTypes.CONNECTION_RESPONSE).get().toJSON();


        MessageEnvelope<ConnectionResponse> connectionResponseEnvelope = MessageEnvelope.parseFromString(newcomerConnectionResponseString, CONNECTION_RESPONSE);
        // Newcomer decrypts the connection response
        ConnectionResponse connectionResponse = newcomerCodec.decryptMessage(connectionResponseEnvelope).get();

        // Newcomer accepts connection response
        newcomer.acceptConnectionResponse(connectionResponse, connectionResponseEnvelope.getDid()).get();

        // Faber needs a new DID to interact with identity owners, thus create a new DID request steward to write on ledger
        String verinymRequest = newcomerCodec.encryptMessage(newcomer.createVerinymRequest(connectionResponse.getDid()), IndyMessageTypes.VERINYM).get().toJSON();

        // #step 4.2.5 t/m 4.2.8
        // Steward accepts verinym request from Faber and thus writes the new DID on the ledger
        steward.acceptVerinymRequest(stewardCodec.decryptMessage(MessageEnvelope.parseFromString(verinymRequest, VERINYM)).get()).get();
    }

    public boolean needsSeeding() {
        return !done;
    }
}
