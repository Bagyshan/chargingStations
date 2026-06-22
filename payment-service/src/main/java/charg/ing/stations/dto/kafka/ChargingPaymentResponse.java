package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reply to {@link ChargingPaymentRequest} on {@code charging.payment.responses}, carrying the user's
 * current wallet balance so station-controll-service can compute the max kWh budget for a session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargingPaymentResponse {
    private UUID requestId;
    private UUID userId;
    private BigDecimal balance;
    private boolean success;
    private String errorMessage;
}
