package charg.ing.stations.dto;


import charg.ing.stations.enums.ActionType;
import charg.ing.stations.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StartTransactionCreateEvent {
    private Integer transactionId;
    private String chargeBoxId;
    private Integer connectorId;
    private String ocppTag;
    private String startValue;
    private ActionType actionType;
    private TransactionStatus status;
    private Instant startTimestamp;
}
