package nl.quintor.studybits.service;

import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.indy.wrapper.dto.ConnectionRequest;
import nl.quintor.studybits.indy.wrapper.message.MessageEnvelopeCodec;
import nl.quintor.studybits.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import javax.management.relation.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StudentUserDetailService implements UserDetailsService {
    @Autowired
    public StudentService studentService;

    public UserDetails loadUserByUsername(String studentId) {

        Student student = studentService.getStudentByStudentId(studentId);

        // If there is no studentID provided in the login message, create one for future purposes.
        if(studentId == null || studentId.isEmpty() || studentId.equals("")) {
            student = studentService.createStudent(UUID.randomUUID().toString(), "", null);
        } else {
            if(student.hasDid()) {
                throw new AccessDeniedException("Access denied for student: " + studentId + ". DID already exists.");
            }
        }

        return User.withUsername(student.getStudentId()).password(student.getPassword()).roles("USER").build();
    }
}
