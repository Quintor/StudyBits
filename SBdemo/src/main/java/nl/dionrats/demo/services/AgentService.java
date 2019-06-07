package nl.dionrats.demo.services;

import lombok.extern.slf4j.Slf4j;
import nl.dionrats.demo.repositories.RedisRepository;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class AgentService {

//    private final RedisRepository redisRepository;
//
//    @Autowired
//    public AgentService(RedisRepository redisRepository) {
//        this.redisRepository = redisRepository;
//    }

    public MessageEnvelope login(String message) {

        log.debug("LOGIN", message);

        //redisRepository.save(message);

//        MessageEnvelope messageEnvelope = null;
//        try {
//            messageEnvelope = MessageEnvelope.parseFromString(message, IndyMessageTypes.CONNECTION_REQUEST);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        log.debug("LOGIN", messageEnvelope);
        return null;
    }
}
