package nl.quintor.studybits.indy.wrapper.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@AllArgsConstructor
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Filter implements Serializable {
    @JsonProperty( "cred_def_id" )
    private String credDefId;
}
