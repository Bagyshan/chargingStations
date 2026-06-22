package charg.ing.stations.dto;

import charg.ing.stations.enums.ActionType;
import charg.ing.stations.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponseDTO {
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

    // Charging budget resolved before start (set by StationController pre-check); persisted on the entity.
    private BigDecimal pricePerKwh;
    private BigDecimal maxKwQuantity;
}
