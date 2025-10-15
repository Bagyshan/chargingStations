package citrine.os.station.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RemoteStartRequest {
    @JsonProperty("chargeBoxId")
    private String chargeBoxId;

    @JsonProperty("connectorId")
    private Integer connectorId;

    @JsonProperty("ocppTag")
    private String ocppTag;
}
