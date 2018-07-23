package nl.quintor.studybits;

import nl.quintor.studybits.entity.ExchangePosition;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.entity.Transcript;
import nl.quintor.studybits.indy.wrapper.dto.AttributeInfo;
import nl.quintor.studybits.indy.wrapper.dto.PredicateInfo;
import nl.quintor.studybits.indy.wrapper.dto.ProofRequest;
import nl.quintor.studybits.repository.ExchangePositionRepository;
import nl.quintor.studybits.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class Seeder {
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ExchangePositionRepository exchangePositionRepository;

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    @EventListener
    public void seed(ContextRefreshedEvent event) {
        seed();
    }

    public void seed() {
        if (isEmpty())
            if (universityName.equals("Rijksuniversiteit Groningen")) {
                Student student = new Student();
                student.setStudentId("12345678");
                student.setFirstName("Lisa");
                student.setLastName("Veren");
                student.setTranscript(new Transcript("Bachelor of Arts, Marketing", "enrolled", "8", false));
                studentRepository.saveAndFlush(student);
            }
    }

    private boolean isEmpty() {
        return studentRepository.count() == 0 && exchangePositionRepository.count() == 0;
    }
}
