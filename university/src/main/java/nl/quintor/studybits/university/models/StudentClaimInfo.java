package nl.quintor.studybits.university.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.ResourceSupport;

@Data
@EqualsAndHashCode( callSuper = true )
@AllArgsConstructor
@NoArgsConstructor
public class StudentClaimInfo extends ResourceSupport {

    private Long claimId;

    private String name;

    private String version;

    private String label;
}
