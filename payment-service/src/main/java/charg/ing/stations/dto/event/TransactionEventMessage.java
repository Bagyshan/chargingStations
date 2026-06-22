package charg.ing.stations.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Charging lifecycle event consumed from {@code transaction.events} (produced by
 * station-controll-service). On start we flag the wallet {@code is_charging=true}; on stop we debit
 * {@code totalSum} and clear the flag.
 *
 * <p>Shares its fully-qualified name with the station-controll-service copy so the JSON type header
 * resolves on both sides; {@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps it tolerant of
 * extra fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private BigDecimal totalSum;
    private Instant createdAt;
    private Instant updatedAt;
}
