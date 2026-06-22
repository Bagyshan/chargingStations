package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted to {@code booking.balance.updated} when a user's wallet is credited (e.g. an O!Dengi
 * top-up is approved) while a booking is active. booking-service consumes it to extend the
 * active reservation under the new balance.
 *
 * <p>Shares its fully-qualified name with the booking-service copy so the JSON type header
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
