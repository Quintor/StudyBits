package nl.quintor.studybits.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.quintor.studybits.LedgerSeeder;
import nl.quintor.studybits.Seeder;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.repository.ExchangePositionRepository;
import nl.quintor.studybits.repository.StudentRepository;
import nl.quintor.studybits.service.CredentialDefinitionService;
import nl.quintor.studybits.service.ExchangePositionService;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.CredDefAlreadyExistsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(value = "/bootstrap", produces = "application/json")
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

    @Autowired
    private Seeder seeder;

    @Autowired(required = false)
    private LedgerSeeder ledgerSeeder;

    private String credDefId;

    @PostMapping("/credential_definition/{schemaId}")
    public void createCredentialDefinition(@PathVariable("schemaId") String schemaId) throws IndyException, ExecutionException, InterruptedException, JsonProcessingException {
        try {
            credentialDefinitionService.createCredentialDefintion(schemaId);
        }
        catch (ExecutionException e) {
            // This is totally fine
            if (!(e.getCause() instanceof CredDefAlreadyExistsException)) {
                throw e;
            }
        }
    }

    @PostMapping("/create_student/{studentId}")
    public String createStudentPosition(@PathVariable("studentId") String studentId) throws JsonProcessingException {
        Student student = new Student();
        student.setStudentId(studentId);
        student.setFirstName("Lisa");
        student.setLastName("Veren");
        student.setPassword(bCryptPasswordEncoder.encode("test1234"));
        student.setStudentDid(null);

        studentRepository.saveAndFlush(student);

        return studentRepository.getStudentByStudentId(studentId).toString();
    }

    @PostMapping("/exchange_position/{credDefId}")
    public void createExchangePosition(@PathVariable("credDefId") String credDefId) throws JsonProcessingException {
        exchangePositionService.createExchangePosition(credDefId);
        this.credDefId = credDefId;
    }

    @PostMapping("/reset")
    public void reset() throws JsonProcessingException {
        studentRepository.deleteAll();
        exchangePositionRepository.deleteAll();
        seeder.seed();
        if (credDefId  != null) {
            exchangePositionService.createExchangePosition(credDefId);
        }
    }


    @GetMapping("/ready")
    public boolean isReady() {
        return ledgerSeeder != null && !ledgerSeeder.needsSeeding();
    }
}
