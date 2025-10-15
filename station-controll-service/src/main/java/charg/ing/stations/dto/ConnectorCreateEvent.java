package charg.ing.stations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConnectorCreateEvent {
    private String chargeBoxId;
    private Integer connectorId;
    private String info;
    private String actionType;
    private Instant timestamp;
    private String vendorId;
}
