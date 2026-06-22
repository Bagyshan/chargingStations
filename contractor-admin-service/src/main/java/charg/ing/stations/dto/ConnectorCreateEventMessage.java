package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConnectorCreateEventMessage {
    private String chargeBoxId;
    private Integer connectorId;
    private String info;
    private String actionType;
    private Long timestamp;
    private String vendorId;
}
