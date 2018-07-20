package nl.quintor.studybits;

import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.entity.Transcript;
import nl.quintor.studybits.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class Seeder {
    @Autowired
    private StudentRepository studentRepository;

    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    @EventListener
    public void seed(ContextRefreshedEvent event) {
        if (isEmpty() && universityName.equals("Rijksuniversiteit Groningen")) {
            Student student = new Student();
            student.setStudentId("12345678");
            student.setFirstName("Lisa");
            student.setLastName("Veren");
            student.setTranscript(new Transcript("Bachelor of Arts, Marketing", "enrolled", "8", false));
            studentRepository.saveAndFlush(student);
        }
    }

    private boolean isEmpty() {
        return studentRepository.count() == 0;
    }
}
