package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.filter.session.SessionFilter;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.Prover;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import nl.quintor.studybits.indy.wrapper.util.SeedUtil;
import nl.quintor.studybits.messages.AuthcryptableExchangePositions;
import nl.quintor.studybits.messages.StudyBitsMessageTypes;
import nl.quintor.studybits.service.ExchangePositionService;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.pool.Pool;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.restassured.RestAssured.given;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_REQUEST;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_RESPONSE;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CREDENTIAL_OFFERS;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.CoreMatchers.is;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Slf4j
public class ScenarioIT {
    static final String ENDPOINT_RUG = "http://localhost:8080";
    static final String ENDPOINT_GENT = "http://localhost:8081";
    static IndyPool indyPool;
    static IndyWallet studentWallet;
    static MessageEnvelopeCodec studentCodec;
    static SessionFilter sessionFilter = new SessionFilter();

    @BeforeClass
    public static void bootstrapBackend() throws Exception {
        Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();

        String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
        indyPool = new IndyPool(poolName);

        studentWallet = IndyWallet.create(indyPool, "student" + System.currentTimeMillis(), SeedUtil.generateSeed());

        studentCodec = new MessageEnvelopeCodec(studentWallet);

        boolean ready = false;
        while (!ready) {
            ready = givenCorrectHeaders(ENDPOINT_RUG)
                    .get("/bootstrap/ready")
                    .then()
                    .assertThat().statusCode(200)
                    .extract().as(Boolean.class);
        }

        ready = false;
        while (!ready) {
            ready = givenCorrectHeaders(ENDPOINT_GENT)
                    .get("/bootstrap/ready")
                    .then()
                    .assertThat().statusCode(200)
                    .extract().as(Boolean.class);
        }
    }

    @Test
    public void test1_Connect() throws IndyException, ExecutionException, InterruptedException, IOException {
        StudyBitsMessageTypes.init();
        IndyMessageTypes.init();

        String universityVerinymDid = "SYqJSzcfsJMhSt7qjcQ8CC";

        // Student creates a connectionRequest
        ConnectionRequest connectionRequest = studentWallet.createConnectionRequest(universityVerinymDid).get();

        // Student logs in to university
        MessageEnvelope<ConnectionResponse> connectionResponseMessageEnvelope = givenCorrectHeaders(ENDPOINT_RUG)
                .queryParam("student_id", "12345678")
                .body(studentCodec.encryptMessage(connectionRequest, CONNECTION_REQUEST).get().toJSON())
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        //New DID created by student
        String studentDid = connectionRequest.getDid();

        // Decrypt and accept connection response
        studentWallet.acceptConnectionResponse(studentCodec.decryptMessage(connectionResponseMessageEnvelope).get(), connectionResponseMessageEnvelope.getDid()).get();

        // TODO: Fix getting the name through did-endpoint resolution
    }

    @Test
    public void test2_obtainingCredential() throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        MessageEnvelope<CredentialOfferList> credentialOfferEnvelopes = givenCorrectHeaders(ENDPOINT_RUG)
                .get("/agent/credential_offer")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        assertThat(credentialOfferEnvelopes.getMessageType(), is(CREDENTIAL_OFFERS));

        CredentialOfferList credentialOffers = studentCodec.decryptMessage(credentialOfferEnvelopes).get();

        assertThat(credentialOffers.getCredentialOffers().isEmpty(), is(false));

        CredentialOffer credentialOffer = credentialOffers.getCredentialOffers().get(0);
        credentialOffer.setTheirDid(credentialOfferEnvelopes.getDid());
        assertThat(credentialOffer.getSchemaId(), notNullValue());

        Prover prover = new Prover(studentWallet, "master_secret_name");
        prover.init();

        CredentialRequest credentialRequest = prover.createCredentialRequest(credentialOffers.getCredentialOffers().get(0)).get();

        MessageEnvelope authcryptedCredentialRequestEnvelope = studentCodec.encryptMessage(credentialRequest, IndyMessageTypes.CREDENTIAL_REQUEST).get();

        MessageEnvelope<CredentialWithRequest> credentialEnvelope = givenCorrectHeaders(ENDPOINT_RUG)
                .body(authcryptedCredentialRequestEnvelope)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        assertThat(credentialEnvelope.getMessageType().getURN(), is(equalTo(IndyMessageTypes.CREDENTIAL.getURN())));

        CredentialWithRequest credentialWithRequest = studentCodec.decryptMessage(credentialEnvelope).get();

        prover.storeCredential(credentialWithRequest).get();

        Credential credential = credentialWithRequest.getCredential();

        assertThat(credential.getValues().get("degree").get("raw").asText(), is(equalTo("Bachelor of Arts, Marketing")));
        assertThat(credential.getValues().get("average").get("raw").asText(), is(equalTo("8")));
        assertThat(credential.getValues().get("status").get("raw").asText(), is(equalTo("enrolled")));

        credentialOfferEnvelopes = givenCorrectHeaders(ENDPOINT_RUG)
                .get("/agent/credential_offer")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);
        credentialOffers = studentCodec.decryptMessage(credentialOfferEnvelopes).get();
        assertThat(credentialOffers.getCredentialOffers().isEmpty(), is(true));
    }

    @Test
    public void test3_ConnectGent() throws IndyException, ExecutionException, InterruptedException, IOException {
        String universityVerinymDid = "Vumgc4B8hFq7n5VNAnfDAL";

        // Student creates a connectionRequest
        ConnectionRequest connectionRequest = studentWallet.createConnectionRequest(universityVerinymDid).get();

        // Student logs in to university
        MessageEnvelope<ConnectionResponse> connectionResponseMessageEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .body(studentCodec.encryptMessage(connectionRequest, CONNECTION_REQUEST).get().toJSON())
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        //New DID created by student
        String studentDid = connectionRequest.getDid();

        // Decrypt and accept connection response
        studentWallet.acceptConnectionResponse(studentCodec.decryptMessage(connectionResponseMessageEnvelope).get(), connectionResponseMessageEnvelope.getDid());

        // TODO: Fix getting the name through did-endpoint resolution

        MessageEnvelope<CredentialOfferList> credentialOfferEnvelopes = givenCorrectHeaders(ENDPOINT_GENT)
                .get("/agent/credential_offer")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        CredentialOfferList credentialOffers = studentCodec.decryptMessage(credentialOfferEnvelopes).get();
        assertThat(credentialOffers.getCredentialOffers().isEmpty(), is(true));
    }

    @Test
    public void test4_getExchangePositionsAndApply() throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        MessageEnvelope<AuthcryptableExchangePositions> exchangePositionsMessageEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .get("/agent/exchange_position")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        AuthcryptableExchangePositions authcryptableExchangePositions = studentCodec.decryptMessage(exchangePositionsMessageEnvelope).get();

        List<ExchangePositionService.ExchangePositionDto> exchangePositions = authcryptableExchangePositions.getExchangePositions();

        assertThat(exchangePositions, hasSize(1));
        assertThat(exchangePositions.get(0).getName(), is(equalTo("MSc Marketing")));
        assertThat(exchangePositions.get(0).isFulfilled(), is(equalTo(false)));

        ProofRequest proofRequest = exchangePositions.get(0).getProofRequest();
        proofRequest.setTheirDid(authcryptableExchangePositions.getTheirDid());



        Prover prover = new Prover(studentWallet, "master_secret_name");
        Map<String, String> values = new HashMap<>();

        Proof proof = prover.fulfillProofRequest(proofRequest, values).get();

        MessageEnvelope proofEnvelope = studentCodec.encryptMessage(proof, IndyMessageTypes.PROOF).get();

        givenCorrectHeaders(ENDPOINT_GENT)
                .body(proofEnvelope)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200);

        exchangePositionsMessageEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .get("/agent/exchange_position")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);
        authcryptableExchangePositions = studentCodec.decryptMessage(exchangePositionsMessageEnvelope).get();

        exchangePositions = authcryptableExchangePositions.getExchangePositions();

        assertThat(exchangePositions, hasSize(1));
        assertThat(exchangePositions.get(0).isFulfilled(), is(equalTo(true)));
    }

    static RequestSpecification givenCorrectHeaders(String endpoint) {
        return given()
                .baseUri(endpoint)
                .header("Accept", "application/json")
                .header("Content-type", "application/json")
                .filter(sessionFilter)
                .filter(new ResponseLoggingFilter());
    }
}