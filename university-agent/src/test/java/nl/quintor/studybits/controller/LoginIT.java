package nl.quintor.studybits.controller;

import io.restassured.filter.session.SessionFilter;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.dto.ConnectionRequest;
import nl.quintor.studybits.indy.wrapper.dto.ConnectionResponse;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import nl.quintor.studybits.indy.wrapper.util.SeedUtil;
import nl.quintor.studybits.messages.StudyBitsMessageTypes;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.pool.Pool;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static nl.quintor.studybits.controller.ScenarioIT.givenCorrectHeaders;
import static nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes.CONNECTION_REQUEST;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Slf4j
public class LoginIT {
    static String universityVerinymDid = "SYqJSzcfsJMhSt7qjcQ8CC";
    static final String ENDPOINT_RUG = "http://localhost:8080";
    static IndyPool indyPool;
    static IndyWallet studentWallet1;
    static IndyWallet studentWallet2;
    static MessageEnvelopeCodec studentCodec1;
    static MessageEnvelopeCodec studentCodec2;
    static SessionFilter sessionFilter = new SessionFilter();

    @BeforeClass
    public static void bootstrapBackend() throws Exception {
        Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();

        String poolName = PoolUtils.createPoolLedgerConfig(null, "testPool" + System.currentTimeMillis());
        indyPool = new IndyPool(poolName);

        studentWallet1 = IndyWallet.create(indyPool, "student1" + System.currentTimeMillis(), SeedUtil.generateSeed()); //Random Student
        studentWallet2 = IndyWallet.create(indyPool, "student2" + System.currentTimeMillis(), "Student0000000000000000000000000"); //Lisa

        System.out.println("studentWallet1 DID: " + studentWallet1.getMainDid());
        System.out.println("studentWallet2 DID: " + studentWallet2.getMainDid());

        studentCodec1 = new MessageEnvelopeCodec(studentWallet1);
        studentCodec2 = new MessageEnvelopeCodec(studentWallet2);

        boolean ready = false;
        while (!ready) {
            ready = givenCorrectHeaders(ENDPOINT_RUG)
                    .get("/bootstrap/ready")
                    .then()
                    .assertThat().statusCode(200)
                    .extract().as(Boolean.class);
        }
    }

    @Test
    //Connect to a university without having a student login
    public void test1_Register() throws IndyException, ExecutionException, InterruptedException, IOException {
        StudyBitsMessageTypes.init();
        IndyMessageTypes.init();

        // Student creates a connectionRequest
        ConnectionRequest connectionRequest = studentWallet1.createConnectionRequest(universityVerinymDid).get();
        // Student registers
        MessageEnvelope<ConnectionResponse> connectionResponseMessageEnvelope = givenCorrectHeaders(ENDPOINT_RUG)
                .body(studentCodec1.encryptMessage(connectionRequest, CONNECTION_REQUEST).get().toJSON())
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        //New DID created by student
        log.debug("Test1 StudentDID: " + connectionRequest.getDid());
        String studentDid = connectionRequest.getDid();

        // Decrypt and accept connection response
        studentWallet1.acceptConnectionResponse(studentCodec1.decryptMessage(connectionResponseMessageEnvelope).get(), connectionResponseMessageEnvelope.getDid()).get();
    }

    @Test
    //Connect to a university while having a student login
    public void test2_Login() throws IndyException, ExecutionException, InterruptedException, IOException {
        StudyBitsMessageTypes.init();
        IndyMessageTypes.init();

        // Student creates a connectionRequest
        ConnectionRequest connectionRequest = studentWallet2.createConnectionRequest(universityVerinymDid).get();

        // Student logs in to university with wrong password
        givenCorrectHeaders(ENDPOINT_RUG)
                .queryParam("student_id", "12345678")
                .queryParam("password", "WRONG_PASSWORD")
                .body(studentCodec2.encryptMessage(connectionRequest, CONNECTION_REQUEST).get().toJSON())
                .post("/agent/login")
                .then()
                .assertThat().statusCode(403);

        // Student logs in to university with correct password
        MessageEnvelope<ConnectionResponse> connectionResponseMessageEnvelope = givenCorrectHeaders(ENDPOINT_RUG)
                .queryParam("student_id", "12345678")
                .queryParam("password", "test1234")
                .body(studentCodec2.encryptMessage(connectionRequest, CONNECTION_REQUEST).get().toJSON())
                .post("/agent/login")
                .then()
                .assertThat().statusCode(200)
                .extract().as(MessageEnvelope.class);

        //New DID created by student
        String studentDid = connectionRequest.getDid();
        log.debug("Test2 StudentDID: " + connectionRequest.getDid());
        // Decrypt and accept connection response
        studentWallet2.acceptConnectionResponse(studentCodec2.decryptMessage(connectionResponseMessageEnvelope).get(), connectionResponseMessageEnvelope.getDid()).get();
    }
}
