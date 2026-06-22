package citrine.os.station.dto;


import citrine.os.station.enums.ActionType;
import citrine.os.station.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OcppResponse {
    private UUID correlationId;
    private Integer transactionId;
    private String chargeBoxId;
    private Integer connectorId;
    private String ocppTag;
    private String startValue;
    private String stopValue;
    private ActionType actionType;
    private TransactionStatus status;
    private String userId;
    private Instant startTimestamp;
    private Instant stopTimestamp;
    private String errorMessage;
}