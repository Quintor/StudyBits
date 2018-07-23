package nl.quintor.studybits.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExchangePosition {
    @Id
    @GeneratedValue
    private long id;

    @Column
    private String name;

    @Column
    private String studentDid;

    @Lob
    private String proofRequestTemplate;

}
