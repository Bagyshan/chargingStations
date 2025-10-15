package citrine.os.station.dto;

import citrine.os.station.enums.ActionType;
import citrine.os.station.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OcppStopResponse {
    private Integer transactionId;
    private String chargeBoxId;
    private Integer connectorId;
    private String ocppTag;
    private String startValue;
    private String stopValue;
    private ActionType actionType;
    private TransactionStatus status;
    private Instant stopTimestamp;


}