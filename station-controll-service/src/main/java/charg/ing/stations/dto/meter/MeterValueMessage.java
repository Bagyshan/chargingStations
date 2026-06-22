package charg.ing.stations.dto.meter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/** Mirror of the {@code station.meter.values} payload produced by station-steve (OCPP MeterValues). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeterValueMessage {
    private String chargeBoxId;
    private Integer connectorId;
    private List<PayloadItem> payload;
    private String actionType;
    private long timestamp;
}
