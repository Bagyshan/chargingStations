package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumed from {@code payment.events}: a user's wallet was credited (e.g. an approved O!Dengi
 * top-up) while a booking is active. booking-service reacts by extending the active reservation
 * under the new balance.
 *
 * <p>Shares its fully-qualified name with the payment-service copy so the JSON type header
 * resolves on both sides (same convention as {@link PaymentResponse}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdatedEvent {
    private UUID userId;
    private BigDecimal newBalance;
    private Instant timestamp;
}
