package nl.quintor.studybits.messages;

import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.message.MessageType;
import nl.quintor.studybits.indy.wrapper.message.MessageTypes;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class StudyBitsMessageTypes {
    private static String STUDYBITS_URN_PREFIX = "urn:studybits:sov:agent:message_type:quintor.nl/";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    public static MessageType<AuthcryptableExchangePositions> EXCHANGE_POSITIONS = new IndyMessageTypes.StandardMessageType<>(
            STUDYBITS_URN_PREFIX + "exchange_position/1.0/exchangePositions", MessageType.Encryption.AUTHCRYPTED, null, AuthcryptableExchangePositions.class);

    public static void init() {
        log.debug("Trying to initialize message types");
        if (!initialized.get()) {
            if(initialized.compareAndSet(false, true)) {
                log.debug("Initializing message types");
                MessageTypes.registerType(EXCHANGE_POSITIONS);
            }
        }
    }
}
