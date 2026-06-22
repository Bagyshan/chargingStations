package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationRequest {
    private UUID requestId;
    private String stationId;
    private Integer connectorId;
}