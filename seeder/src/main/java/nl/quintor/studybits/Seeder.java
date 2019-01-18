package nl.quintor.studybits;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.dto.ConnectionRequest;
import nl.quintor.studybits.indy.wrapper.dto.ConnectionResponse;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.pool.Pool;
import org.springframework.http.ResponseEntity;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.springframework.web.client.RestTemplate;

import nl.quintor.studybits.indy.wrapper.util.SeedUtil;

import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_REQUEST;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_RESPONSE;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.VERINYM;

public class Seeder {
    public static IndyPool indyPool;

    @Command(name = "seed", description = "Generate random seed.")
    static class SeedCommand implements Runnable {
        @Override
        public void run() {
            System.out.println(SeedUtil.generateSeed());
        }
    }

    @Command(name = "did", description = "Output DID based on a seed.")
    static class DidCommand implements Runnable {
        @Parameters(paramLabel = "<seed>", description = "Seed from university")
        private String seed;

        @Override
        public void run() {
            try {
                IndyWallet indyWallet = IndyWallet.create(indyPool, "wallet" + System.currentTimeMillis(), seed);
                System.out.println(indyWallet.getMainDid());
            } catch (Exception e) {
                exception(e);
            }
        }
    }

    @Command(name = "onboard", description = "Onboard a university.")
    static class OnboardCommand implements Runnable {
        @Parameters(paramLabel = "<seed>", description = "Seed from university")
        private String seed;

        @Override
        public void run() {
            try {
                String name = "university" + System.currentTimeMillis();

                IndyWallet stewardWallet = IndyWallet.create(indyPool, "steward" + System.currentTimeMillis(), "000000000000000000000000Steward1");
                TrustAnchor steward = new TrustAnchor(stewardWallet);

                Issuer university = new Issuer(IndyWallet.create(indyPool, name, seed));

                onboardIssuer(steward, university);
                System.out.println("Onboarded univsersity with name " + name );
            } catch (Exception e) {
                exception(e);
            }
        }
    }

    @Command(name = "schema", description = "Define schema and return schema ID.")
    static class SchemaCommand implements Runnable {
        @Parameters(paramLabel = "<seed>", description = "Seed from university")
        private String seed;

        @Override
        public void run() {
            try {
                IndyWallet stewardWallet = IndyWallet.create(indyPool, "university" + System.currentTimeMillis(),  seed);
                Issuer trustAnchorIssuer = new Issuer(stewardWallet);
                String schemaId = trustAnchorIssuer.createAndSendSchema("Transcript", "1.0", "first_name", "last_name", "degree", "status", "average").get();
                System.out.println(schemaId);
            } catch (Exception e) {
                exception(e);
            }
        }
    }


    @Command(name = "cred-def", description = "Define schema and return schema ID.")
    static class CredDefCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "<seed>", description = "Seed from university")
        private String seed;

        @Parameters(index = "1", paramLabel = "<schema-id>", description = "Schema ID")
        private String schemaId;


        @Override
        public void run() {
            try {
                IndyWallet trustAnchorWallet = IndyWallet.create(indyPool, "university" + System.currentTimeMillis(), seed);
                Issuer trustAnchorIssuer = new Issuer(trustAnchorWallet);
                String credentialDefinitionId = trustAnchorIssuer.defineCredential(schemaId).get();

                System.out.println(credentialDefinitionId);
            } catch (Exception e) {
                exception(e);
            }
        }
    }

    @Command(name = "exchange-position", description = "Exchange position.")
    static class ExchangePositionCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "<domain>", description = "Domain from the target university")
        private String domain;

        @Parameters(index = "1", paramLabel = "<cred-def-id>", description = "Credential definition ID")
        private String creddefId;


        @Override
        public void run() {
            try {
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<String> response = restTemplate.postForEntity(domain+"/bootstrap/exchange_position/" + creddefId, null, String.class);

                System.out.println(response.toString());
            } catch (Exception e) {
                exception(e);
            }
        }
    }


    @Command(name = "student", description = "Create student position.")
    static class StudentCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "<domain>", description = "Domain from the target university")
        private String domain;

        @Parameters(index = "1", paramLabel = "<studentId>", description = "StudenId")
        private String studentId;

        @Override
        public void run() {
            try {
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<String> response = restTemplate.postForEntity(domain+"/bootstrap/create_student/" + studentId, null, String.class);

                System.out.println(response.toString());
            } catch (Exception e) {
                exception(e);
            }
        }
    }

    static void exception(Exception e) {
        System.out.println(e.getMessage());
    }

    @Command(name = "", subcommands = {SeedCommand.class, DidCommand.class, OnboardCommand.class, SchemaCommand.class, CredDefCommand.class, ExchangePositionCommand.class, StudentCommand.class})
    static class ParentCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    public static void main(String[] args) throws IndyException, InterruptedException, ExecutionException, IOException {
        Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();
        String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
        indyPool = new IndyPool(poolName);

        CommandLine cmd = new CommandLine(new ParentCommand());
        cmd.parseWithHandler(new CommandLine.RunAll(), args);

        if (args.length == 0) {
            cmd.usage(System.out);
        }
    }

    public static void onboardIssuer(TrustAnchor steward, Issuer newcomer) throws InterruptedException, ExecutionException, IndyException, IOException {
        // Create Codecs to facilitate encryption/decryption
        MessageEnvelopeCodec stewardCodec = new MessageEnvelopeCodec(steward);
        MessageEnvelopeCodec newcomerCodec = new MessageEnvelopeCodec(newcomer);

        // Connecting newcomer with Steward

        // We revert the order from the tutorial, since we use the anoncryption from the verinym

        // Create connection request for steward
        String connectionRequestString = newcomerCodec.encryptMessage(newcomer.createConnectionRequest().get(),
                IndyMessageTypes.CONNECTION_REQUEST, steward.getMainDid()).get().toJSON();

        // Steward decrypts connection request
        ConnectionRequest connectionRequest = stewardCodec.decryptMessage(MessageEnvelope.parseFromString(connectionRequestString, CONNECTION_REQUEST)).get();

        // Steward accepts connection request
        ConnectionResponse newcomerConnectionResponse = steward.acceptConnectionRequest(connectionRequest).get();

        // Steward sends a connection response
        String newcomerConnectionResponseString =  stewardCodec.encryptMessage(newcomerConnectionResponse, IndyMessageTypes.CONNECTION_RESPONSE, connectionRequest.getDid()).get().toJSON();


        MessageEnvelope<ConnectionResponse> connectionResponseEnvelope = MessageEnvelope.parseFromString(newcomerConnectionResponseString, CONNECTION_RESPONSE);
        // Newcomer decrypts the connection response
        ConnectionResponse connectionResponse = newcomerCodec.decryptMessage(connectionResponseEnvelope).get();

        // Newcomer accepts connection response
        newcomer.acceptConnectionResponse(connectionResponse, connectionResponseEnvelope.getDid()).get();

        // Faber needs a new DID to interact with identity owners, thus create a new DID request steward to write on ledger
        String verinymRequest = newcomerCodec.encryptMessage(newcomer.createVerinymRequest(connectionResponse.getDid()), IndyMessageTypes.VERINYM, connectionResponse.getDid()).get().toJSON();

        // #step 4.2.5 t/m 4.2.8
        // Steward accepts verinym request from Faber and thus writes the new DID on the ledger
        steward.acceptVerinymRequest(stewardCodec.decryptMessage(MessageEnvelope.parseFromString(verinymRequest, VERINYM)).get()).get();
    }
}