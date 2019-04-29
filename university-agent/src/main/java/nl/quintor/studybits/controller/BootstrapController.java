package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.entity.Transcript;
import nl.quintor.studybits.repository.ExchangePositionRepository;
import nl.quintor.studybits.repository.StudentRepository;
import nl.quintor.studybits.service.CredentialDefinitionService;
import nl.quintor.studybits.service.ExchangePositionService;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.CredDefAlreadyExistsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(value = "/bootstrap", produces = "application/json")
@Slf4j
public class BootstrapController {
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private CredentialDefinitionService credentialDefinitionService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ExchangePositionRepository exchangePositionRepository;

    @Autowired
    private ExchangePositionService exchangePositionService;

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    private String credDefId;

    @PostMapping("/credential_definition/{schemaId}")
    public String createCredentialDefinition(@PathVariable("schemaId") String schemaId) throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        try {
            return credentialDefinitionService.createCredentialDefintion(schemaId);
        }
        catch (ExecutionException e) {
            // This is totally fine
            if (!(e.getCause() instanceof CredDefAlreadyExistsException)) {
                throw e;
            }
        }
        return null;
    }

    @PostMapping("/create_student/{studentId}")
    public String createStudentPosition(@PathVariable("studentId") String studentId) throws JsonProcessingException {
        Student student = new Student();
        student.setStudentId(studentId);
        student.setFirstName("Lisa");
        student.setLastName("Veren");
        student.setPassword(bCryptPasswordEncoder.encode("test1234"));
        student.setStudentDid(null);
        student.setTranscript(new Transcript("Bachelor of Arts, Marketing", "enrolled", "8", false));

        studentRepository.saveAndFlush(student);

        return studentRepository.getStudentByStudentId(studentId).toString();
    }

    @PostMapping("/exchange_position/{credDefId}")
    public void createExchangePosition(@PathVariable("credDefId") String credDefId) throws JsonProcessingException {
        log.debug("Creating exchange postion {}", credDefId);
        exchangePositionService.createExchangePosition(credDefId);
        this.credDefId = credDefId;
    }

    @PostMapping("/reset")
    public void reset() throws JsonProcessingException {
        studentRepository.deleteAll();
        exchangePositionRepository.deleteAll();
        if (universityName.equals("Rijksuniversiteit Groningen")) {
            createStudentPosition("12345678");
        }
        if (credDefId  != null) {
            exchangePositionService.createExchangePosition(credDefId);
        }
    }


    @GetMapping("/ready")
    public boolean isReady() {
        return true;
    }
}
