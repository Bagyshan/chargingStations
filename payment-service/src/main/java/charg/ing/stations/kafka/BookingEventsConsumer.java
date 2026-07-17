package charg.ing.stations.kafka;

import charg.ing.stations.audit.AuditEventPublisher;
import charg.ing.stations.dto.event.BookingEventMessage;
import charg.ing.stations.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventsConsumer {

    private final UserBalanceRepository balanceRepository;
    private final AuditEventPublisher auditPublisher;

    @KafkaListener(topics = "booking.events", groupId = "payment-service-group")
    public void handleBookingEvent(BookingEventMessage event) {
        log.info("Received booking event: {}", event);

        if (event.getEventType() == BookingEventMessage.EventType.START_RESERVATION) {
            balanceRepository.findByUserId(event.getUserId())
                    .flatMap(balance -> {
                        balance.setBooking(true);
                        return balanceRepository.save(balance);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.error("User balance not found for userId: {}", event.getUserId());
                        return Mono.empty();
                    }))
                    .subscribe(
                            saved -> log.info("Balance updated for user {}: new balance = {}", event.getUserId(), saved.getBalance()),
                            error -> log.error("Error updating balance for user {}: {}", event.getUserId(), error.getMessage())
                    );
        }

        if (event.getEventType() == BookingEventMessage.EventType.STOP_RESERVATION) {
            balanceRepository.findByUserId(event.getUserId())
                    .flatMap(balance -> {
                        BigDecimal newBalance = balance.getBalance().subtract(event.getTotalSum());
                        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                            log.warn("Balance would become negative for user {}, setting to 0", event.getUserId());
                            newBalance = BigDecimal.ZERO;
                        }
                        balance.setBalance(newBalance);
                        balance.setBooking(false); // освобождаем флаг бронирования
                        return balanceRepository.save(balance);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.error("User balance not found for userId: {}", event.getUserId());
                        return Mono.empty();
                    }))
                    .subscribe(
                            saved -> {
                                log.info("Balance updated for user {}: new balance = {}", event.getUserId(), saved.getBalance());
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("debit", event.getTotalSum());
                                payload.put("newBalance", saved.getBalance());
                                payload.put("reason", "BOOKING");
                                auditPublisher.publishBalance("DEBIT", event.getUserId(), "INFO",
                                        "Booking settlement -" + event.getTotalSum(), payload);
                            },
                            error -> log.error("Error updating balance for user {}: {}", event.getUserId(), error.getMessage())
                    );
        }
        // START_RESERVATION можно игнорировать или установить isBooking = true при необходимости
    }
}