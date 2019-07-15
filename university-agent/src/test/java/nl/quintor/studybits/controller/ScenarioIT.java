package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CREDENTIAL_OFFERS;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.GET_REQUEST;
import static nl.quintor.studybits.messages.StudyBitsMessageTypes.EXCHANGE_POSITIONS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsNull.notNullValue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Slf4j
public class ScenarioIT {

    static String rugVerinymDid = "SYqJSzcfsJMhSt7qjcQ8CC";
    static String gentVerinymDid = "Vumgc4B8hFq7n5VNAnfDAL";

    static final String ENDPOINT_RUG = "http://localhost:8080";
    static final String ENDPOINT_GENT = "http://localhost:8081";
    static IndyPool indyPool;

    static IndyWallet studentWallet;
    static MessageEnvelopeCodec studentCodec;

    static String rugLisaDid = null;
    static String gentLisaDid = null;
    static CredentialOfferList studentCredentialOfferList = null;

    static Prover studentProver = null;


    @BeforeClass
    public static void bootstrapBackend() throws Exception {
        Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();

        String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
        indyPool = new IndyPool(poolName);

        studentWallet = IndyWallet.create(indyPool, "studentLisa" + System.currentTimeMillis(), "Student0000000000000000000000000"); //Lisa

        System.out.println("studentWallet DID: " + studentWallet.getMainDid());

        studentCodec = new MessageEnvelopeCodec(studentWallet);
        studentProver = new Prover(studentWallet, "master_secret_name");

        // Resetting is actually not needed when running once, but is useful for repeatedly running tests in development
        // Reset 'Lisa', the dummy student
        givenCorrectHeaders(ENDPOINT_RUG)
            .post("/bootstrap/reset")
            .then()
            .assertThat().statusCode(200);

        // We are resetting Gent, since we need to recreate the exchangeposition
        givenCorrectHeaders(ENDPOINT_GENT)
                .post("/bootstrap/reset")
                .then()
                .assertThat().statusCode(200);

        boolean ready = false;
        while (!ready) {
            ready = givenCorrectHeaders(ENDPOINT_RUG)
                    .get("/bootstrap/ready")
                    .then()
                    .assertThat().statusCode(200)
                    .extract().as(Boolean.class);
        }

        studentProver.init();
    }

    @Test
    //Lisa registers to Gent for future purposes
    public void test1_Register() throws IndyException, ExecutionException, InterruptedException, IOException {
        StudyBitsMessageTypes.init();
        IndyMessageTypes.init();

        // Student creates a connectionRequest
        ConnectionRequest connectionRequest = studentWallet.createConnectionRequest().get();
        // Student registers
        MessageEnvelope<ConnectionResponse> connectionResponseMessageEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .body(studentCodec.encryptMessage(connectionRequest, CONNECTION_REQUEST, gentVerinymDid).get().toJSON())
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        ConnectionResponse connectionResponse = studentCodec.decryptMessage(connectionResponseMessageEnvelope).get();
        //New DID created by student
        log.debug("Lisa Gent DID: " + connectionResponse.getDid());
        gentLisaDid = connectionResponse.getDid();
        // Decrypt and accept connection response
        studentWallet.acceptConnectionResponse(connectionResponse, connectionResponseMessageEnvelope.getDid()).get();
    }
    
    @Test
    //Connect to RUG while having a student login
    public void test2_Login() throws IndyException, ExecutionException, InterruptedException, IOException {
        StudyBitsMessageTypes.init();
        IndyMessageTypes.init();

        // Student creates a connectionRequest
        ConnectionRequest connectionRequest = studentWallet.createConnectionRequest().get();

        //Uses their public DID to encrypt message

        // Student logs in to university with wrong password
        givenCorrectHeaders(ENDPOINT_RUG, "12345678", "WRONG_PASSWORD")
                .body(studentCodec.encryptMessage(connectionRequest, CONNECTION_REQUEST, rugVerinymDid).get().toJSON())
                .post("/agent/login")
                .then()
                .assertThat().statusCode(401);

        // Student logs in to university with correct password
        MessageEnvelope<ConnectionResponse> connectionResponseMessageEnvelope = givenCorrectHeaders(ENDPOINT_RUG, "12345678", "test1234")
                .body(studentCodec.encryptMessage(connectionRequest, CONNECTION_REQUEST, rugVerinymDid).get().toJSON())
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        ConnectionResponse connectionResponse = studentCodec.decryptMessage(connectionResponseMessageEnvelope).get();

        //New DID created by student
        log.debug("Lisa RUG DID: " + connectionResponse.getDid());
        rugLisaDid = connectionResponse.getDid();
        // Decrypt and accept connection response
        studentWallet.acceptConnectionResponse(connectionResponse, connectionResponseMessageEnvelope.getDid()).get();
    }

    @Test
    public void test3_GetCredentialOffers() throws IndyException, ExecutionException, InterruptedException, IOException {
        //Request CREDENTIAL_OFFERS
        String getRequest = studentCodec.encryptMessage(CREDENTIAL_OFFERS.getURN(), GET_REQUEST, rugLisaDid).get().toJSON();

        MessageEnvelope<CredentialOfferList> credentialOfferEnvelopes = givenCorrectHeaders(ENDPOINT_RUG)
                .body(getRequest)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        assertThat(credentialOfferEnvelopes.getMessageType(), is(CREDENTIAL_OFFERS));

        studentCredentialOfferList = studentCodec.decryptMessage(credentialOfferEnvelopes).get();

        assertThat(studentCredentialOfferList.getCredentialOffers(), hasSize(1));

        CredentialOffer credentialOffer = studentCredentialOfferList.getCredentialOffers().get(0);

        assertThat(credentialOffer.getSchemaId(), notNullValue());
    }

    @Test
    public void test4_CredentialRequest() throws IndyException, JsonProcessingException, ExecutionException, InterruptedException {

        CredentialRequest credentialRequest = studentProver.createCredentialRequest(rugLisaDid, studentCredentialOfferList.getCredentialOffers().get(0)).get();

        MessageEnvelope authcryptedCredentialRequestEnvelope = studentCodec.encryptMessage(credentialRequest, IndyMessageTypes.CREDENTIAL_REQUEST, rugLisaDid).get();

        MessageEnvelope<Credential> credentialEnvelope = givenCorrectHeaders(ENDPOINT_RUG)
                .body(authcryptedCredentialRequestEnvelope.toJSON())
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        assertThat(credentialEnvelope.getMessageType().getURN(), is(equalTo(IndyMessageTypes.CREDENTIAL.getURN())));

        Credential credential = studentCodec.decryptMessage(credentialEnvelope).get();

        studentProver.storeCredential(credentialRequest, credential).get();

        assertThat(credential.getValues().get("degree").get("raw").asText(), is(equalTo("Bachelor of Arts, Marketing")));
        assertThat(credential.getValues().get("average").get("raw").asText(), is(equalTo("8")));
        assertThat(credential.getValues().get("status").get("raw").asText(), is(equalTo("enrolled")));

        String getRequest = studentCodec.encryptMessage(CREDENTIAL_OFFERS.getURN(), GET_REQUEST, rugLisaDid).get().toJSON();

        MessageEnvelope<CredentialOfferList> credentialOfferEnvelopes = givenCorrectHeaders(ENDPOINT_RUG)
                .body(getRequest)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        studentCredentialOfferList = studentCodec.decryptMessage(credentialOfferEnvelopes).get();
        assertThat(studentCredentialOfferList.getCredentialOffers().isEmpty(), is(true));
    }

    @Test
    public void test5_getExchangePositionsAndApply() throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {
        String getRequest = studentCodec.encryptMessage(EXCHANGE_POSITIONS.getURN(), GET_REQUEST, gentLisaDid).get().toJSON();
        MessageEnvelope<AuthcryptableExchangePositions> exchangePositionsMessageEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .body(getRequest)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        AuthcryptableExchangePositions authcryptableExchangePositions = studentCodec.decryptMessage(exchangePositionsMessageEnvelope).get();

        List<ExchangePositionService.ExchangePositionDto> exchangePositions = authcryptableExchangePositions.getExchangePositions();

        assertThat(exchangePositions, hasSize(1));
        assertThat(exchangePositions.get(0).getName(), is(equalTo("MSc Marketing")));
        assertThat(exchangePositions.get(0).isFulfilled(), is(equalTo(false)));

        ProofRequest proofRequest = exchangePositions.get(0).getProofRequest();

        Map<String, String> values = new HashMap<>();

        Proof proof = studentProver.fulfillProofRequest(proofRequest, values).get();

        MessageEnvelope proofEnvelope = studentCodec.encryptMessage(proof, IndyMessageTypes.PROOF, gentLisaDid).get();

        givenCorrectHeaders(ENDPOINT_GENT)
                .body(proofEnvelope)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200);

        exchangePositionsMessageEnvelope = givenCorrectHeaders(ENDPOINT_GENT)
                .body(getRequest)
                .post("/agent/message")
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
                .auth().preemptive().basic("", "");

    }


    static RequestSpecification givenCorrectHeaders(String endpoint, String username, String password) {
        return given()
                .baseUri(endpoint)
                .auth().basic(username, password);
    }
}
