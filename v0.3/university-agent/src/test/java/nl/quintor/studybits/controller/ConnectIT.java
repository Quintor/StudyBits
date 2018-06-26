package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import io.restassured.specification.RequestSpecification;
import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.dto.*;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class ConnectIT {
    static final String ENDPOINT = "http://localhost:8080";
    static IndyPool indyPool;


    @BeforeClass
    public static void bootstrapBackend() throws InterruptedException, ExecutionException, IndyException, IOException {
        String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
        indyPool = new IndyPool(poolName);
        TrustAnchor steward = new TrustAnchor(IndyWallet.create(indyPool, "steward_wallet" + System.currentTimeMillis(), "000000000000000000000000Steward1"));

        Issuer university = new Issuer(IndyWallet.create(indyPool, "university_wallet" + System.currentTimeMillis(), StringUtils.leftPad("rug", 32, '0')));

        // Connecting newcomer with Steward
        String governmentConnectionRequest = steward.createConnectionRequest(university.getName(), "TRUST_ANCHOR").get().toJSON();

        AnoncryptedMessage newcomerConnectionResponse = university.acceptConnectionRequest(JSONUtil.mapper.readValue(governmentConnectionRequest, ConnectionRequest.class))
                .thenCompose(AsyncUtil.wrapException(university::anonEncrypt))
                .get();

        steward.anonDecrypt(newcomerConnectionResponse, ConnectionResponse.class)
                .thenCompose(AsyncUtil.wrapException(steward::acceptConnectionResponse)).get();

        AuthcryptedMessage verinym = university.authEncrypt(university.createVerinymRequest(JSONUtil.mapper.readValue(governmentConnectionRequest, ConnectionRequest.class)
                .getDid()))
                .get();

        steward.authDecrypt(verinym, Verinym.class)
                .thenCompose(AsyncUtil.wrapException(steward::acceptVerinymRequest)).get();
    }


    @Test
    public void testConnect() throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        IndyWallet studentWallet = IndyWallet.create(indyPool, "student_wallet" + System.currentTimeMillis(), null);

        MessageEnvelope connectionRequestEnvelope = givenCorrectHeaders()
                .post("/agent/login/12345678")
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


    static RequestSpecification givenCorrectHeaders() {
        return given()
                .baseUri(ENDPOINT)
                .header("Accept", "application/json")
                .header("Content-type", "application/json");
    }
}