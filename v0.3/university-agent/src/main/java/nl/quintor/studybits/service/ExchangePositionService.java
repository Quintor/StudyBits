package nl.quintor.studybits.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.quintor.studybits.entity.ExchangePosition;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.indy.wrapper.dto.AttributeInfo;
import nl.quintor.studybits.indy.wrapper.dto.Filter;
import nl.quintor.studybits.indy.wrapper.dto.ProofRequest;
import nl.quintor.studybits.indy.wrapper.util.AsyncUtil;
import nl.quintor.studybits.indy.wrapper.util.JSONUtil;
import nl.quintor.studybits.repository.ExchangePositionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Component
public class ExchangePositionService {
    @Autowired
    private ExchangePositionRepository exchangePositionRepository;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private StudentService studentService;

    private static final Random random = new Random();

    @Transactional
    public void createExchangePosition(String credDefId) throws JsonProcessingException {
        List<Filter> transcriptFilter = Collections.singletonList(new Filter(credDefId));
        ProofRequest exchangePositionProofRequest = ProofRequest.builder()
                .name("ExchangePosition")
                .nonce("1432422343242122312411212")
                .version("0.1")
                .requestedAttribute("attr1_referent", new AttributeInfo("first_name", Optional.empty()))
                .requestedAttribute("attr2_referent", new AttributeInfo("last_name", Optional.empty()))
                .requestedAttribute("attr3_referent", new AttributeInfo("degree", Optional.of(transcriptFilter)))
                .requestedAttribute("attr4_referent", new AttributeInfo("status", Optional.of(transcriptFilter)))
                .build();

        ExchangePosition exchangePosition = new ExchangePosition();
        exchangePosition.setName("MSc Marketing");
        exchangePosition.setProofRequestTemplate(exchangePositionProofRequest.toJSON());
        exchangePositionRepository.saveAndFlush(exchangePosition);
    }


    @Transactional
    public List<ExchangePositionDto> getAll() {
        Student student = studentService.getStudentByStudentId(identityService.getStudentId());
        return exchangePositionRepository.findAll()
                .stream()
                .map(AsyncUtil.wrapException(exchangePosition -> {
                    ProofRequest proofRequest = JSONUtil.mapper.readValue(exchangePosition.getProofRequestTemplate(), ProofRequest.class);

                    proofRequest.setNonce(Long.toString(random.nextLong()));
                    proofRequest.setTheirDid(student.getStudentDid());

                    return new ExchangePositionDto(exchangePosition.getName(), proofRequest);
                }))
                .collect(Collectors.toList());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExchangePositionDto {
        private String name;
        private ProofRequest proofRequest;
    }
}
