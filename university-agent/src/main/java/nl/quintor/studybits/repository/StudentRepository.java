package nl.quintor.studybits.repository;

import nl.quintor.studybits.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    public Student getStudentByRequestNonce(String requestNonce);
    public Student getStudentByStudentId(String studentId);
    public Student getStudentByStudentDid(String studentDid);
}
