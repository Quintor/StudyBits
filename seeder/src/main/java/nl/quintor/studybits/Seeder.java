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
    //STN (Sovrin Test Net)
    //https://raw.githubusercontent.com/sovrin-foundation/sovrin/stable/sovrin/pool_transactions_sandbox_genesis
    //REGISTER @ https://s3.us-east-2.amazonaws.com/evernym-cs/sovrin-STNnetwork/www/trust-anchor.html
    public static boolean stn = true;
    public static IndyPool indyPool;

    @Command(name = "seed", description = "Generate random seed.")
    static class SeedCommand implements Runnable {
        @Override
        public void run() {
            System.out.println(SeedUtil.generateSeed());
        }
    }

    @Command(name = "did", description = "Output DID based on a seed.", helpCommand = true)
    static class DidCommand implements Runnable {
        @Parameters(paramLabel = "<seed>", description = "Seed from wallet")
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

    @Command(name = "verkey", description = "Output VerKey based on a seed.", helpCommand = true)
    static class VerKeyCommand implements Runnable {
        @Parameters(paramLabel = "<seed>", description = "Seed from wallet")
        private String seed;

        @Override
        public void run() {
            try {
                IndyWallet indyWallet = IndyWallet.create(indyPool, "wallet" + System.currentTimeMillis(), seed);
                System.out.println(indyWallet.getMainKey());
            } catch (Exception e) {
                exception(e);
            }
        }
    }
    

    @Command(name = "onboard", description = "Onboard a university.")
    static class OnboardCommand implements Runnable {
        @Parameters(paramLabel = "<seed>", description = "Seed from wallet")
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
        @Parameters(paramLabel = "<seed>", description = "Seed from wallet")
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


    @Command(name = "cred-def", description = "Create credential defenition from schema ID and return credential defenitial ID")
    static class CredDefCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "<seed>", description = "Seed from wallet")
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


    @Command(name = "student", description = "Create student.")
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

    private static void exception(Exception e) {
        System.out.println(e.getMessage());
    }

    @Command(name = "", subcommands = {SeedCommand.class, DidCommand.class, VerKeyCommand.class, OnboardCommand.class, SchemaCommand.class, CredDefCommand.class, ExchangePositionCommand.class, StudentCommand.class})
    static class ParentCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    public static void main(String[] args) throws IndyException, InterruptedException, ExecutionException, IOException {
        Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();
        String poolName;

        if(stn) {
            String[] stnTxns = new String[]{
                    String.format("{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"australia\",\"client_ip\":\"52.64.96.160\",\"client_port\":\"9702\",\"node_ip\":\"52.64.96.160\",\"node_port\":\"9701\",\"services\":[\"VALIDATOR\"]},\"dest\":\"UZH61eLH3JokEwjMWQoCMwB3PMD6zRBvG6NCv5yVwXz\"},\"metadata\":{\"from\":\"3U8HUen8WcgpbnEz1etnai\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":1,\"txnId\":\"c585f1decb986f7ff19b8d03deba346ab8a0494cc1e4d69ad9b8acb0dfbeab6f\"},\"ver\":\"1\"}"),
                    String.format("{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"brazil\",\"client_ip\":\"54.233.203.241\",\"client_port\":\"9702\",\"node_ip\":\"54.233.203.241\",\"node_port\":\"9701\",\"services\":[\"VALIDATOR\"]},\"dest\":\"2MHGDD2XpRJohQzsXu4FAANcmdypfNdpcqRbqnhkQsCq\"},\"metadata\":{\"from\":\"G3knUCmDrWd1FJrRryuKTw\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":2,\"txnId\":\"5c8f52ca28966103ff0aad98160bc8e978c9ca0285a2043a521481d11ed17506\"},\"ver\":\"1\"}"),
                    String.format("{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"canada\",\"client_ip\":\"52.60.207.225\",\"client_port\":\"9702\",\"node_ip\":\"52.60.207.225\",\"node_port\":\"9701\",\"services\":[\"VALIDATOR\"]},\"dest\":\"8NZ6tbcPN2NVvf2fVhZWqU11XModNudhbe15JSctCXab\"},\"metadata\":{\"from\":\"22QmMyTEAbaF4VfL7LameE\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":3,\"txnId\":\"408c7c5887a0f3905767754f424989b0089c14ac502d7f851d11b31ea2d1baa6\"},\"ver\":\"1\"}"),
                    String.format("{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"england\",\"client_ip\":\"52.56.191.9\",\"client_port\":\"9702\",\"node_ip\":\"52.56.191.9\",\"node_port\":\"9701\",\"services\":[\"VALIDATOR\"]},\"dest\":\"DNuLANU7f1QvW1esN3Sv9Eap9j14QuLiPeYzf28Nub4W\"},\"metadata\":{\"from\":\"NYh3bcUeSsJJcxBE6TTmEr\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":4,\"txnId\":\"d56d0ff69b62792a00a361fbf6e02e2a634a7a8da1c3e49d59e71e0f19c27875\"},\"ver\":\"1\"}"),
                    String.format("{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"korea\",\"client_ip\":\"52.79.115.223\",\"client_port\":\"9702\",\"node_ip\":\"52.79.115.223\",\"node_port\":\"9701\",\"services\":[\"VALIDATOR\"]},\"dest\":\"HCNuqUoXuK9GXGd2EULPaiMso2pJnxR6fCZpmRYbc7vM\"},\"metadata\":{\"from\":\"U38UHML5A1BQ1mYh7tYXeu\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":5,\"txnId\":\"76201e78aca720dbaf516d86d9342ad5b5d46f5badecf828eb9edfee8ab48a50\"},\"ver\":\"1\"}"),
                    String.format("{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"singapore\",\"client_ip\":\"13.228.62.7\",\"client_port\":\"9702\",\"node_ip\":\"13.228.62.7\",\"node_port\":\"9701\",\"services\":[\"VALIDATOR\"]},\"dest\":\"Dh99uW8jSNRBiRQ4JEMpGmJYvzmF35E6ibnmAAf7tbk8\"},\"metadata\":{\"from\":\"HfXThVwhJB4o1Q1Fjr4yrC\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":6,\"txnId\":\"51e2a46721d104d9148d85b617833e7745fdbd6795cb0b502a5b6ea31d33378e\"},\"ver\":\"1\"}"),
                    String.format("{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"virginia\",\"client_ip\":\"34.225.215.131\",\"client_port\":\"9702\",\"node_ip\":\"34.225.215.131\",\"node_port\":\"9701\",\"services\":[\"VALIDATOR\"]},\"dest\":\"EoGRm7eRADtHJRThMCrBXMUM2FpPRML19tNxDAG8YTP8\"},\"metadata\":{\"from\":\"SPdfHq6rGcySFVjDX4iyCo\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":7,\"txnId\":\"0a4992ea442b53e3dca861deac09a8d4987004a8483079b12861080ea4aa1b52\"},\"ver\":\"1\"}"),
            };

            poolName = PoolUtils.createPoolLedgerConfigFromTxns(stnTxns, "STN" + System.currentTimeMillis());
        } else {
            poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
        }



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
}