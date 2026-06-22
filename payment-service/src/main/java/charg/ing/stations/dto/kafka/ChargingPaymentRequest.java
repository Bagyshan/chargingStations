package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request-reply (key = requestId) from station-controll-service asking for a user's wallet balance
 * before starting a charging transaction. Mirrors the booking {@link PaymentRequest} flow but on the
 * dedicated {@code charging.payment.requests} topic.
 *
 * <p>Shares its fully-qualified name with the station-controll-service copy so the JSON type header
 * resolves on both sides.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargingPaymentRequest {
    private UUID requestId;
    private UUID userId;
}
