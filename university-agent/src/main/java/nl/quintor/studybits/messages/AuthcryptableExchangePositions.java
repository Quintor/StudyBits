package nl.quintor.studybits.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.quintor.studybits.indy.wrapper.dto.AuthCryptable;
import nl.quintor.studybits.service.ExchangePositionService;

import java.util.List;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class AuthcryptableExchangePositions implements AuthCryptable {
    private List<ExchangePositionService.ExchangePositionDto> exchangePositions;

    @JsonIgnore
    @Setter
    private String theirDid;
}
