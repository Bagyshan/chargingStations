package charg.ing.stations.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEventMessage {

    private Long id;
    private Integer transactionId;
    private String chargeBoxId;
    private Integer connectorId;
    private Instant startTimestamp;
    private Integer startValue;
    private Instant stopTimestamp;
    private Integer stopValue;
    private Integer transactionValue;
    private String status;
    private String reason;
    private String userId;
    private BigDecimal totalSum;
    private Instant createdAt;
    private Instant updatedAt;
}
