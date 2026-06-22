package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    private Instant createdAt;
    private Instant updatedAt;
}
