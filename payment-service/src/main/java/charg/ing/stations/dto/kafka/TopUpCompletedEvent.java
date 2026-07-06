package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted to {@code payment.topup.events} on EVERY successfully credited top-up
 * (unlike {@link BalanceUpdatedEvent}, which only fires while a booking/charging
 * session is active). notification-service consumes it to push
 * «Кошелёк пополнен» to the user's devices.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopUpCompletedEvent {
    private UUID userId;
    private BigDecimal amount;
    private BigDecimal newBalance;
    private Instant timestamp;
}
