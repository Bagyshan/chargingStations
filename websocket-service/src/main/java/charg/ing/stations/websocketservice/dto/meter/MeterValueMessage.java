package charg.ing.stations.websocketservice.dto.meter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeterValueMessage {
    @JsonProperty("chargeBoxId")
    private String chargeBoxId;

    @JsonProperty("connectorId")
    private Integer connectorId;

    @JsonProperty("payload")
    private List<PayloadItem> payload;

    @JsonProperty("actionType")
    private String actionType;

    @JsonProperty("timestamp")
    private long timestamp;
}