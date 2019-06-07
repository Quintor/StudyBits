package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.dto.ConnectionResponse;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.LibIndy;
import org.hyperledger.indy.sdk.pool.Pool;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class test {

    private static final String LIB_PATH = "/Users/dionrats/Nextcloud/school/Leerjaar_4/Stage/studybits/indy-sdk/libindy/target/debug";

    static IndyPool indyPool;
    static IndyWallet studentWallet;
    static MessageEnvelopeCodec studentCodec;

    @BeforeClass
    public static void setup(){
        LibIndy.init(LIB_PATH);
    }

    @Before
    public void init() {
        try {
            log.debug("Initialising Test");
            Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();

            String poolName = PoolUtils.createPoolLedgerConfig("10.40.121.131", "testPool" + System.currentTimeMillis());
            indyPool = new IndyPool(poolName);

            log.debug("\tInitialized: Pool[name: {}]", poolName);

            studentWallet = IndyWallet.create(indyPool, "studentLisa" + System.currentTimeMillis(), "Student0000000000000000000000000"); //Lisa
            log.debug("\tInitialized: studentWallet[DID: {}]", studentWallet.getMainDid());
            studentCodec = new MessageEnvelopeCodec(studentWallet);

        } catch (InterruptedException | ExecutionException | IndyException | IOException e) {
            e.printStackTrace();
        }


    }

    @Test
    public void decryptMessageTest() {
        log.debug("======================");
        log.debug("Decrypt test");
        log.debug("======================");

        String message = "UPW1A68AezhJnWU2PEzXRcR4354l/9OAbaA6Ygh3fUTyfdZKV4l2fn6Johp6QkPOVWy86Sqsn6HLfVBE8SanHPg/JRa6ERffxcieHPX5cZnrtKnJWL4cP7iLRHimWYXNdRYzK76MCAXY9Jhi1+MZ6n3NB6vzBIipJSnb7ZvSppP5UNpIOFuyQw==";
        MessageEnvelope messageEnvelope = null;
        try {
            messageEnvelope = MessageEnvelope.parseFromString(message);
            log.debug("Envelope: {}", messageEnvelope);
            ConnectionResponse connectionResponse = (ConnectionResponse) studentCodec.decryptMessage(messageEnvelope).get();

            log.debug("decrypted: {}", connectionResponse);
        } catch (IOException | IndyException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }


        log.debug("======================");
    }
}
