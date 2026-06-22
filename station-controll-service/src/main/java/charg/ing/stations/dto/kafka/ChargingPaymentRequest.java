package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Sent to {@code charging.payment.requests} to fetch a user's wallet balance before starting a
 * charging transaction. Correlated by {@code requestId}; the reply arrives on
 * {@code charging.payment.responses}.
 *
 * <p>Shares its fully-qualified name with the payment-service copy so the JSON type header resolves
 * on the consumer side.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargingPaymentRequest {
    private UUID requestId;
    private UUID userId;
}
