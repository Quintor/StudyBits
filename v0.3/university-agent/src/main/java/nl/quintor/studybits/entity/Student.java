package nl.quintor.studybits.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Student {
    @Id
    @GeneratedValue
    private long id;

    @Column(unique = true)
    private String studentId;

    @Column
    private String firstName;

    @Column
    private String lastName;

    @Column
    private String requestNonce;

    @Column
    private String studentDid;

    @Column
    private String myDid;

    @Embedded
    private Transcript transcript;
}
