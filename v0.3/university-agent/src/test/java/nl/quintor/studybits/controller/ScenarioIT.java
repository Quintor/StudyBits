package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.filter.session.SessionFilter;
import io.restassured.specification.RequestSpecification;
import nl.quintor.studybits.indy.wrapper.*;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ScenarioIT {
    static final String ENDPOINT = "http://localhost:8080";
    static IndyPool indyPool;
    static IndyWallet stewardWallet;
    static IndyWallet studentWallet;
    static SessionFilter sessionFilter = new SessionFilter();

    static String schemaId;

    @BeforeClass
    public static void bootstrapBackend() throws InterruptedException, ExecutionException, IndyException, IOException {
        String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
        indyPool = new IndyPool(poolName);
        stewardWallet = IndyWallet.create(indyPool, "steward_wallet" + System.currentTimeMillis(), "000000000000000000000000Steward1");
        TrustAnchor steward = new TrustAnchor(stewardWallet);

        Issuer university = new Issuer(IndyWallet.create(indyPool, "university_wallet" + System.currentTimeMillis(), StringUtils.leftPad("rug", 32, '0')));

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
        schemaId = stewardIssuer.createAndSendSchema("Transcript", "1.0", "degree", "status", "average").get();
        System.out.println(sessionFilter.getSessionId());
        givenCorrectHeaders()
                .when()
                .post("/bootstrap/credential_definition/{schemaId}", schemaId)
                .then()
                .assertThat().statusCode(200);

        studentWallet = IndyWallet.create(indyPool, "student_wallet" + System.currentTimeMillis(), null);
    }


    @Test
    public void test1_Connect() throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        MessageEnvelope connectionRequestEnvelope = givenCorrectHeaders()
                .queryParam("student_id", "12345678")
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        ConnectionRequest connectionRequest = JSONUtil.mapper.treeToValue(connectionRequestEnvelope.getMessage(), ConnectionRequest.class);

        AnoncryptedMessage anoncryptedConnectionResponse = studentWallet.acceptConnectionRequest(connectionRequest)
                .thenCompose(AsyncUtil.wrapException(studentWallet::anonEncrypt)).get();

        MessageEnvelope connectionResponseEnvelope = new MessageEnvelope(connectionRequest.getRequestNonce(), MessageEnvelope.MessageType.CONNECTION_RESPONSE,
                new TextNode(Base64.getEncoder().encodeToString(anoncryptedConnectionResponse.getMessage())));

        MessageEnvelope connectionAcknowledgementEnvelope = givenCorrectHeaders()
                .body(connectionResponseEnvelope)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        assertThat(connectionAcknowledgementEnvelope.getType(), is(equalTo(MessageEnvelope.MessageType.CONNECTION_ACKNOWLEDGEMENT)));
    }

    @Test
    public void test2_obtainingCredential() throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        MessageEnvelope[] credentialOfferEnvelopes = givenCorrectHeaders()
                .get("/agent/credential_offer")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope[].class);

        assertThat(credentialOfferEnvelopes, arrayWithSize(equalTo(1)));

        AuthcryptedMessage authcryptedCredentialOffer = new AuthcryptedMessage(Base64.getDecoder().decode(credentialOfferEnvelopes[0].getMessage().asText()), credentialOfferEnvelopes[0].getId());

        CredentialOffer credentialOffer = studentWallet.authDecrypt(authcryptedCredentialOffer, CredentialOffer.class).get();
        assertThat(credentialOffer.getSchemaId(), is(equalTo(schemaId)));

        Prover prover = new Prover(studentWallet, "master_secret_name");
        prover.init();

        AuthcryptedMessage authcryptedCredentialRequest = prover.createCredentialRequest(credentialOffer)
                .thenCompose(AsyncUtil.wrapException(prover::authEncrypt)).get();

        MessageEnvelope authcryptedCredentialRequestEnvelope = new MessageEnvelope(authcryptedCredentialRequest.getDid(), MessageEnvelope.MessageType.CREDENTIAL_REQUEST,
                new TextNode(Base64.getEncoder().encodeToString(authcryptedCredentialRequest.getMessage())));

        MessageEnvelope credentialEnvelope = givenCorrectHeaders()
                .body(authcryptedCredentialRequestEnvelope)
                .post("/agent/message")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        assertThat(credentialEnvelope.getType(), is(equalTo(MessageEnvelope.MessageType.CREDENTIAL)));

        AuthcryptedMessage authcryptedCredential = new AuthcryptedMessage(Base64.getDecoder().decode(credentialEnvelope.getMessage().asText()), credentialEnvelope.getId());

        Credential credential = prover.authDecrypt(authcryptedCredential, CredentialWithRequest.class).get().getCredential();

        assertThat(credential.getValues().get("degree").get("raw").asText(), is(equalTo("Bachelor of Arts, Marketing")));
        assertThat(credential.getValues().get("average").get("raw").asText(), is(equalTo("8")));
        assertThat(credential.getValues().get("status").get("raw").asText(), is(equalTo("enrolled")));
    }


    static RequestSpecification givenCorrectHeaders() {
        return given()
                .baseUri(ENDPOINT)
                .header("Accept", "application/json")
                .header("Content-type", "application/json")
                .filter(sessionFilter)
                .filter(new ResponseLoggingFilter());
    }
}