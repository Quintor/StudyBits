package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.filter.session.SessionFilter;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.*;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import nl.quintor.studybits.indy.wrapper.util.SeedUtil;
import nl.quintor.studybits.messages.AuthcryptableExchangePositions;
import nl.quintor.studybits.messages.StudyBitsMessageTypes;
import nl.quintor.studybits.service.ExchangePositionService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.IndyException;
import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static io.restassured.RestAssured.given;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_REQUEST;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_RESPONSE;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.VERINYM;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.hasSize;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Slf4j
public class ScenarioIT {
    static final String ENDPOINT_RUG = "http://localhost:8080";
    static final String ENDPOINT_GENT = "http://localhost:8081";
    static IndyPool indyPool;
    static IndyWallet studentWallet;
    static SessionFilter sessionFilter = new SessionFilter();

    @BeforeClass
    public static void bootstrapBackend() throws Exception {
        Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();

        String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
        indyPool = new IndyPool(poolName);

        studentWallet = IndyWallet.create(indyPool, "student" + System.currentTimeMillis(), SeedUtil.generateSeed());

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

        // Student receives a connectionRequest from the University
        MessageEnvelope<ConnectionRequest> connectionRequestEnvelope = givenCorrectHeaders(ENDPOINT_RUG)
                .queryParam("student_id", "12345678")
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        // Extract the connectionRequest from the Envelope
        ConnectionRequest connectionRequest = connectionRequestEnvelope.extractMessage(null).get();

        // New DID created by university
        log.debug("CONNECTION REQUEST DID: " + connectionRequest.getDid());

        String universityDid = connectionRequest.getDid();
        // Student accepts the connectionRequest from the university and creates a connectionResponse
        ConnectionResponse connectionResponse = studentWallet.acceptConnectionRequest(connectionRequest).get();

        //New DID created by student
        log.debug("CONNECTION RESPONSE DID: " + connectionResponse.getDid());
        String studentDid = connectionResponse.getDid();

        // Put connectionResponse in an Envelope
        MessageEnvelope connectionResponseEnvelope = MessageEnvelope.encryptMessage(connectionResponse, IndyMessageTypes.CONNECTION_RESPONSE, studentWallet).get();

        // Student sends the connectionResponse to the university and should receive a connectionAcknowledgement
        String result = givenCorrectHeaders(ENDPOINT_RUG)
                .body(connectionResponseEnvelope)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().response().asString();

        MessageEnvelope<AuthcryptableString> connectionAcknowledgementEnvelope = MessageEnvelope.parseFromString(result, IndyMessageTypes.CONNECTION_ACKNOWLEDGEMENT);

        assertThat(connectionAcknowledgementEnvelope.getMessageType().getURN(), is(equalTo(IndyMessageTypes.CONNECTION_ACKNOWLEDGEMENT.getURN())));
        assertThat(connectionAcknowledgementEnvelope.extractMessage(studentWallet).get().getPayload(), is(equalTo("Rijksuniversiteit Groningen")));
    }

    @Test
    public void test2_obtainingCredential() throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        MessageEnvelope<CredentialOffer>[] credentialOfferEnvelopes = givenCorrectHeaders(ENDPOINT_RUG)
                .get("/agent/credential_offer")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope[].class);

        assertThat(credentialOfferEnvelopes, arrayWithSize(equalTo(1)));

        CredentialOffer credentialOffer = credentialOfferEnvelopes[0].extractMessage(studentWallet).get();

        assertThat(credentialOffer.getSchemaId(), notNullValue());

        Prover prover = new Prover(studentWallet, "master_secret_name");
        prover.init();

        CredentialRequest credentialRequest = prover.createCredentialRequest(credentialOffer).get();

        MessageEnvelope authcryptedCredentialRequestEnvelope = MessageEnvelope.encryptMessage(credentialRequest, IndyMessageTypes.CREDENTIAL_REQUEST, studentWallet).get();

        MessageEnvelope<CredentialWithRequest> credentialEnvelope = givenCorrectHeaders(ENDPOINT_RUG)
                .body(authcryptedCredentialRequestEnvelope)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        assertThat(credentialEnvelope.getMessageType().getURN(), is(equalTo(IndyMessageTypes.CREDENTIAL.getURN())));

        CredentialWithRequest credentialWithRequest = credentialEnvelope.extractMessage(studentWallet).get();

        prover.storeCredential(credentialWithRequest).get();

        Credential credential = credentialWithRequest.getCredential();

        assertThat(credential.getValues().get("degree").get("raw").asText(), is(equalTo("Bachelor of Arts, Marketing")));
        assertThat(credential.getValues().get("average").get("raw").asText(), is(equalTo("8")));
        assertThat(credential.getValues().get("status").get("raw").asText(), is(equalTo("enrolled")));

        credentialOfferEnvelopes = givenCorrectHeaders(ENDPOINT_RUG)
                .get("/agent/credential_offer")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope[].class);

        assertThat(Arrays.asList(credentialOfferEnvelopes), hasSize(0));
    }

    @Test
    public void test3_ConnectGent() throws IndyException, ExecutionException, InterruptedException, IOException {
        MessageEnvelope<ConnectionRequest> connectionRequestEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        ConnectionRequest connectionRequest = connectionRequestEnvelope.extractMessage(studentWallet).get();

        ConnectionResponse connectionResponse = studentWallet.acceptConnectionRequest(connectionRequest).get();

        MessageEnvelope connectionResponseEnvelope = MessageEnvelope.encryptMessage(connectionResponse, IndyMessageTypes.CONNECTION_RESPONSE, studentWallet).get();

        MessageEnvelope<AuthcryptableString> connectionAcknowledgementEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .body(connectionResponseEnvelope)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        assertThat(connectionAcknowledgementEnvelope.getMessageType().getURN(), is(equalTo(IndyMessageTypes.CONNECTION_ACKNOWLEDGEMENT.getURN())));
        assertThat(connectionAcknowledgementEnvelope.extractMessage(studentWallet).get().getPayload(), is(equalTo("Universiteit Gent")));

        MessageEnvelope[] credentialOfferEnvelopes = givenCorrectHeaders(ENDPOINT_GENT)
                .get("/agent/credential_offer")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope[].class);

        assertThat(Arrays.asList(credentialOfferEnvelopes), hasSize(0));
    }

    @Test
    public void test4_getExchangePositionsAndApply() throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        MessageEnvelope<AuthcryptableExchangePositions> exchangePositionsMessageEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .get("/agent/exchange_position")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        AuthcryptableExchangePositions authcryptableExchangePositions = exchangePositionsMessageEnvelope.extractMessage(studentWallet).get();

        List<ExchangePositionService.ExchangePositionDto> exchangePositions = authcryptableExchangePositions.getExchangePositions();

        assertThat(exchangePositions, hasSize(1));
        assertThat(exchangePositions.get(0).getName(), is(equalTo("MSc Marketing")));
        assertThat(exchangePositions.get(0).isFulfilled(), is(equalTo(false)));

        ProofRequest proofRequest = exchangePositions.get(0).getProofRequest();
        proofRequest.setTheirDid(authcryptableExchangePositions.getTheirDid());



        Prover prover = new Prover(studentWallet, "master_secret_name");
        Map<String, String> values = new HashMap<>();

        Proof proof = prover.fulfillProofRequest(proofRequest, values).get();

        MessageEnvelope proofEnvelope = MessageEnvelope.encryptMessage(proof, IndyMessageTypes.PROOF, prover).get();

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
        authcryptableExchangePositions = exchangePositionsMessageEnvelope.extractMessage(studentWallet).get();

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