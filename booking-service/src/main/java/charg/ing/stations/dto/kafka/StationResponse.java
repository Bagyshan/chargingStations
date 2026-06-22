package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationResponse {
    private UUID requestId;
    private String stationId;
    private Integer connectorId;
    private BigDecimal pricePerMinute;
    private boolean available;
    private String errorMessage;
}