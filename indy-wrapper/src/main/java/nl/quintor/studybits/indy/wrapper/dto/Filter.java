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
    private Optional<String> credDefId;

    @JsonProperty("schema_name")
    private Optional<String> schemaName;
    @JsonProperty("schema_version")
    private Optional<String> schemaVersion;
}
