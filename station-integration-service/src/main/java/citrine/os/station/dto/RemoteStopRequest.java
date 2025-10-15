package citrine.os.station.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class RemoteStopRequest {
    private String chargeBoxId;
    private int connectorId;
    private String ocppTag;
}
