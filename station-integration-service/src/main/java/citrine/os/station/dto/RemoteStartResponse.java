package citrine.os.station.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RemoteStartResponse {
    private Integer transactionId;
    private String chargeBoxId;
    private Integer connectorId;
    private String ocppTag;
    private String startValue;
    private Instant startTimestamp;
}
