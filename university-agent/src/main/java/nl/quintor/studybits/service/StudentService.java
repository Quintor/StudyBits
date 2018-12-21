package nl.quintor.studybits.service;

import nl.quintor.studybits.entity.ExchangePosition;
import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.exceptions.UserAlreadyExistAuthenticationException;
import nl.quintor.studybits.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.UUID;

@Component
public class StudentService {
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Transactional
    public void setConnectionData(String studentId, String myDid) {
        Student student = studentRepository.getStudentByStudentId(studentId);

        if (student == null) {
            throw new EntityNotFoundException("Student not found for studentId: " + studentId);
        }

        student.setMyDid(myDid);
        studentRepository.saveAndFlush(student);
    }

    @Transactional
    public Student getStudentByStudentId(String studentId) {
        return studentRepository.getStudentByStudentId(studentId);
    }

    @Transactional
    public Student getStudentByStudentDid(String studentDid) {
        return studentRepository.getStudentByStudentDid(studentDid);
    }

    @Transactional
    public void proveTranscript(String studentId) {
        Student studentEntity = studentRepository.getStudentByStudentId(
                studentId
        );
        studentEntity.getTranscript().setProven(true);
        studentRepository.saveAndFlush(studentEntity);
    }

    @Transactional
    public void setExchangePositionData(String did, String proofRequest, ExchangePosition exchangePosition) {
        Student studentEntity = studentRepository.getStudentByStudentDid(
                did
        );

        studentEntity.setProofRequest(proofRequest);
        studentEntity.setExchangePosition(exchangePosition);
        studentRepository.saveAndFlush(studentEntity);
    }

    @Transactional
    public void setStudentDid(String studentId, String studentDid) {
        Student student = studentRepository.getStudentByStudentId(studentId);

        if (student == null) {
            throw new EntityNotFoundException("Student not found for studentId: " + studentId);
        }

        student.setStudentDid(studentDid);
        studentRepository.saveAndFlush(student);
    }

    @Transactional
    public Student createStudent() {
        String randomId = UUID.randomUUID().toString();
        if(!studentExists(randomId)) {
            Student student = new Student();
            student.setStudentId(randomId);

            return studentRepository.saveAndFlush(student);
        }

        throw new EntityExistsException();
    }

    @Transactional
    public Student createStudent(String id, String password, String did) {
        if(!studentExists(id)) {
            Student student = new Student();
            student.setStudentId(id);
            String passwordHash = bCryptPasswordEncoder.encode(password);
            student.setPassword(passwordHash);
            student.setStudentDid(did);

            return studentRepository.saveAndFlush(student);
        }
        //TODO: Return this exception as response instead of a 401
        throw new UserAlreadyExistAuthenticationException("Student '"+id+"' already exists");
    }

    public Boolean studentExists(String studentId) {
        if(studentRepository.getStudentByStudentId(studentId) != null) {
            return true;
        }

        return false;
    }

    public Boolean matchPassword(String password, String hashedPassword) {
       return bCryptPasswordEncoder.matches(password, hashedPassword);
    }
}
