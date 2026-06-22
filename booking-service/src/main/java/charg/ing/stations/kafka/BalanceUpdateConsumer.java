package charg.ing.stations.kafka;

import charg.ing.stations.dto.kafka.BalanceUpdatedEvent;
import charg.ing.stations.service.BookingBalanceAdjustmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to {@code payment.events} for wallet top-ups that must adapt an active booking.
 * Runs on the Kafka consumer thread; blocks until the reactive adjustment completes so the offset
 * is only committed after the booking has been updated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceUpdateConsumer {

    private final BookingBalanceAdjustmentService adjustmentService;

    @KafkaListener(topics = "payment.events", groupId = "booking-service-balance-group")
    public void onBalanceUpdated(BalanceUpdatedEvent event) {
        log.info("Received balance update event: {}", event);
        adjustmentService.applyBalanceUpdate(event).block();
    }
}
