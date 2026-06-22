package charg.ing.stations.service;

import charg.ing.stations.dto.event.BookingEvent;
import charg.ing.stations.dto.kafka.BalanceUpdatedEvent;
import charg.ing.stations.entity.BookingEntity;
import charg.ing.stations.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reacts to wallet top-ups that happen while a booking is ACTIVE: extends the reservation under the
 * new balance and pushes a refreshed {@code booking.state} event (so websocket-service forwards the
 * new numbers to the connected client).
 *
 * <p>Prepaid model: the whole wallet funds the session and is only debited on STOP, so the total
 * allowed minutes are recomputed from {@code startedAt} against the new balance. A top-up only grows
 * the balance, hence the window can only extend; a non-increasing recompute is a safe no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingBalanceAdjustmentService {

    private static final String ACTIVE = "ACTIVE";

    private final BookingRepository bookingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Mono<Void> applyBalanceUpdate(BalanceUpdatedEvent event) {
        if (event.getUserId() == null || event.getNewBalance() == null) {
            log.warn("Ignoring balance update without userId/newBalance: {}", event);
            return Mono.empty();
        }
        return bookingRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(event.getUserId(), ACTIVE)
                .flatMap(booking -> adjust(booking, event.getNewBalance()))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("No active booking for user {}, balance update ignored", event.getUserId())))
                .then()
                .onErrorResume(e -> {
                    log.error("Failed to apply balance update for user {}: {}",
                            event.getUserId(), e.getMessage(), e);
                    return Mono.empty();
                });
    }

    private Mono<BookingEntity> adjust(BookingEntity booking, BigDecimal newBalance) {
        BigDecimal pricePerMinute = booking.getPricePerMinute();
        if (pricePerMinute == null || pricePerMinute.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Booking {} has non-positive pricePerMinute, skipping adjustment", booking.getBookingId());
            return Mono.empty();
        }

        int newMax = newBalance.divide(pricePerMinute, RoundingMode.DOWN).intValue();
        int currentMax = booking.getMaxBookingMinutes() == null ? 0 : booking.getMaxBookingMinutes();
        if (newMax <= currentMax) {
            log.debug("Booking {}: recomputed max {} <= current {}, no extension needed",
                    booking.getBookingId(), newMax, currentMax);
            return Mono.empty();
        }

        Instant startedAt = booking.getStartedAt() != null ? booking.getStartedAt() : Instant.now();
        Instant newEnd = startedAt.plusSeconds(newMax * 60L);
        booking.setMaxBookingMinutes(newMax);
        booking.setRemainingBookingEndTime(newEnd);

        log.info("Extending booking {} for user {}: max {} -> {} min, end -> {} (new balance {})",
                booking.getBookingId(), booking.getUserId(), currentMax, newMax, newEnd, newBalance);

        return bookingRepository.save(booking)
                .doOnSuccess(this::sendBalanceUpdatedEvent);
    }

    private void sendBalanceUpdatedEvent(BookingEntity booking) {
        Instant now = Instant.now();

        long minutesElapsed = booking.getStartedAt() != null
                ? Duration.between(booking.getStartedAt(), now).toMinutes()
                : 0;
        minutesElapsed = Math.min(minutesElapsed, booking.getMaxBookingMinutes());

        BigDecimal currentCost = booking.getPricePerMinute()
                .multiply(BigDecimal.valueOf(minutesElapsed));
        int remainingMinutes = booking.getMaxBookingMinutes() - (int) minutesElapsed;
        if (remainingMinutes < 0) remainingMinutes = 0;

        BookingEvent.EventData data = BookingEvent.EventData.builder()
                .stationId(booking.getStationId())
                .connectorId(booking.getConnectorId())
                .pricePerMinute(booking.getPricePerMinute())
                .minutesElapsed((int) minutesElapsed)
                .currentCost(currentCost)
                .maxBookingMinutes(booking.getMaxBookingMinutes())
                .remainingBookingMinutes(remainingMinutes)
                .startedAt(booking.getStartedAt())
                .estimatedEndTime(booking.getRemainingBookingEndTime())
                .build();

        BookingEvent bookingEvent = BookingEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(BookingEvent.EventType.RESERVATION_BALANCE_UPDATED)
                .timestamp(now)
                .userId(booking.getUserId())
                .reservationId(booking.getBookingId())
                .data(data)
                .build();

        kafkaTemplate.send("booking.state", booking.getUserId().toString(), bookingEvent);
        log.info("Sent booking event: {} for booking {}",
                bookingEvent.getEventType(), booking.getBookingId());
    }
}
