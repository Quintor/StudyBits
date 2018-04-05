package nl.quintor.studybits.student.repositories;

import nl.quintor.studybits.student.entities.ConnectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConnectionRecordRepository extends JpaRepository<ConnectionRecord, Long> {
    List<ConnectionRecord> findAllByStudentUserName(String userName);
}
