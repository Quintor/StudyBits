package nl.quintor.studybits.student.repositories;

import nl.quintor.studybits.student.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUsername( String username );

    boolean existsByUsername( String username );
}
