package charg.ing.stations.consumer;

import charg.ing.stations.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Reacts to wallet top-ups during an active charging session: recomputes the kWh budget of the
 * user's active transaction under the new balance. Same {@code payment.events} topic that
 * booking-service listens to; each service handles its own active session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargingBalanceUpdateConsumer {

    private final TransactionService transactionService;

    @KafkaListener(topics = "payment.events", groupId = "station-controller-service-group-balance")
    public void onBalanceUpdate(Map<String, Object> event, Acknowledgment ack) {
        try {
            if (event == null) {
                return;
            }
            Object userId = event.get("userId");
            Object newBalance = event.get("newBalance");
            if (userId == null || newBalance == null) {
                return;
            }
            log.info("Charging: balance update for user {} -> {}", userId, newBalance);
            transactionService.recomputeMaxKw(userId.toString(), new BigDecimal(newBalance.toString()));
        } catch (Exception e) {
            log.error("Failed to process balance update event: {}", e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }
}
