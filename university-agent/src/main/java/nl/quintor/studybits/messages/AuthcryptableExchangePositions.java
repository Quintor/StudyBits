package nl.quintor.studybits.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.quintor.studybits.service.ExchangePositionService;

import java.io.Serializable;
import java.util.List;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class AuthcryptableExchangePositions implements Serializable {
    private List<ExchangePositionService.ExchangePositionDto> exchangePositions;
}
