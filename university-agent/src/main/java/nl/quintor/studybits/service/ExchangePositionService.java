package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.entity.ExchangePosition;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.dto.AttributeInfo;
import nl.quintor.studybits.indy.wrapper.dto.Filter;
import nl.quintor.studybits.indy.wrapper.dto.ProofRequest;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelope;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.messages.AuthcryptableExchangePositions;
import nl.quintor.studybits.messages.StudyBitsMessageTypes;
import nl.quintor.studybits.repository.ExchangePositionRepository;
import org.hyperledger.indy.sdk.IndyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ExchangePositionService {
    @Autowired
    private ExchangePositionRepository exchangePositionRepository;
    @Autowired
    private IdentityService identityService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private MessageEnvelopeCodec universityCodec;

    private static final Random random = new Random();

    @Transactional
    public void createExchangePosition(String credDefId) throws JsonProcessingException {
        List<Filter> transcriptFilter = Collections.singletonList(new Filter(credDefId));
        ProofRequest exchangePositionProofRequest = ProofRequest.builder()
                .name("ExchangePosition")
                .nonce("1432422343242122312411212")
                .version("0.1")
                .requestedAttribute("attr1_referent", new AttributeInfo("first_name", Optional.of(transcriptFilter)))
                .requestedAttribute("attr2_referent", new AttributeInfo("last_name", Optional.of(transcriptFilter)))
                .requestedAttribute("attr3_referent", new AttributeInfo("degree", Optional.of(transcriptFilter)))
                .requestedAttribute("attr4_referent", new AttributeInfo("status", Optional.of(transcriptFilter)))
                .build();

        ExchangePosition exchangePosition = new ExchangePosition();
        exchangePosition.setName("MSc Marketing");
        exchangePosition.setProofRequestTemplate(exchangePositionProofRequest.toJSON());
        exchangePosition.setFulfilled(false);
        exchangePositionRepository.saveAndFlush(exchangePosition);
    }

    @Transactional
    public void fullfillPosition(long id) {
        ExchangePosition exchangePosition = exchangePositionRepository.getOne(id);
        exchangePosition.setFulfilled(true);
        exchangePositionRepository.saveAndFlush(exchangePosition);
    }


    @Transactional
    public MessageEnvelope<AuthcryptableExchangePositions> getAll(String did) throws JsonProcessingException, IndyException, ExecutionException, InterruptedException {

        log.debug("Getting exchange positions for studentDid {}", did);

        Student student = studentService.getStudentByStudentDid(did);
        log.debug("Getting exchange positions for student {}", student);
        if (student == null) {
            throw new AccessDeniedException("Need to be authenticated");
        }

        log.debug("All exchange positions {}", exchangePositionRepository.findAll());

        List<ExchangePositionDto> exchangePositionDtos = exchangePositionRepository.findAll()
                .stream()
                .map(AsyncUtil.wrapException(exchangePosition -> {
                    ProofRequest proofRequest = JSONUtil.mapper.readValue(exchangePosition.getProofRequestTemplate(), ProofRequest.class);

                    proofRequest.setNonce(Long.toString(Math.abs(random.nextLong())));
                    exchangePosition.setStudentDid(student.getStudentDid());
                    studentService.setExchangePositionData(did, proofRequest.toJSON(), exchangePosition);
                    return  new ExchangePositionDto(exchangePosition.getName(), proofRequest, exchangePosition.isFulfilled());
                }))
                .collect(Collectors.toList());

        return universityCodec.encryptMessage(new AuthcryptableExchangePositions(exchangePositionDtos), StudyBitsMessageTypes.EXCHANGE_POSITIONS, student.getStudentDid()).get();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExchangePositionDto {
        private String name;
        private ProofRequest proofRequest;
        private boolean fulfilled;
    }
}
