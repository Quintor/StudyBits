package nl.quintor.studybits.service;

import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

@Component
public class StudentService {
    @Autowired
    private StudentRepository studentRepository;

    @Transactional
    public void setConnectionData(String studentId, String myDid, String requestNonce) {
        Student student = studentRepository.getOne(studentId);

        student.setMyDid(myDid);
        student.setRequestNonce(requestNonce);
        studentRepository.saveAndFlush(student);
    }

    @Transactional
    public String getMyDidByRequestNonce(String requestNonce) {
        return studentRepository.getStudentByRequestNonce(requestNonce).getMyDid();
    }
}
