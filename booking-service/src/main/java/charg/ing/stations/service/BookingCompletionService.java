package charg.ing.stations.service;

import charg.ing.stations.dto.event.BookingEvent;
import charg.ing.stations.dto.event.BookingEventMessage;
import charg.ing.stations.dto.responses.BookingCompleteResponse;
import charg.ing.stations.entity.BookingEntity;
import charg.ing.stations.producer.BookingEventProducer;
import charg.ing.stations.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCompletionService {

    private final BookingRepository bookingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BookingEventProducer bookingEventProducer;

    public Mono<BookingCompleteResponse> completeBooking(UUID userId, UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .switchIfEmpty(Mono.error(new RuntimeException("Booking not found")))
                .flatMap(booking -> {
                    if (!booking.getUserId().equals(userId)) {
                        return Mono.error(new RuntimeException("Booking does not belong to user"));
                    }
                    if (!"ACTIVE".equals(booking.getStatus())) {
                        return Mono.error(new RuntimeException("Booking is not active"));
                    }

                    Instant now = Instant.now();
                    long elapsed = Duration.between(booking.getStartedAt(), now).toMinutes();
                    // Минимум 1 минута: бронь всегда стоит хотя бы 1 минуту.
                    int totalMinutes = (int) Math.max(elapsed, 1);
                    BigDecimal price = booking.getPricePerMinute();
                    BigDecimal fullSum = price.multiply(BigDecimal.valueOf(totalMinutes));
                    // 1 минута уже списана при старте — добираем только остаток.
                    BigDecimal deltaSum = price.multiply(BigDecimal.valueOf(totalMinutes - 1L));

                    booking.setStatus("COMPLETED");
                    booking.setEndedAt(now);
                    booking.setTotalMinutes(totalMinutes);
                    booking.setTotalSum(fullSum);

                    // Событие для WS/истории (полная сумма для отображения).
                    sendCompletionEvent(booking, now, totalMinutes, fullSum);
                    // Событие для payment-service — добор остатка (может быть 0).
                    BookingEventMessage stopEvent = buildStopEvent(booking, totalMinutes, deltaSum);

                    return bookingRepository.save(booking)
                            .flatMap(saved ->
                                    bookingEventProducer.sendBookingEvent(stopEvent)
                                            .thenReturn(saved)
                            )
                            .map(saved -> new BookingCompleteResponse(
                                    saved.getBookingId(),
                                    saved.getStatus(),
                                    saved.getTotalSum(),
                                    saved.getTotalMinutes(),
                                    saved.getStartedAt(),
                                    saved.getEndedAt(),
                                    null
                            ));
                });
    }

    private void sendCompletionEvent(BookingEntity booking, Instant completedAt,
                                     int totalMinutes, BigDecimal currentCost) {
        int remainingMinutes = booking.getMaxBookingMinutes() - totalMinutes;
        if (remainingMinutes < 0) remainingMinutes = 0;

        BookingEvent.EventData data = BookingEvent.EventData.builder()
                .stationId(booking.getStationId())
                .connectorId(booking.getConnectorId())
                .pricePerMinute(booking.getPricePerMinute())
                .minutesElapsed(totalMinutes)
                .currentCost(currentCost)
                .maxBookingMinutes(booking.getMaxBookingMinutes())
                .remainingBookingMinutes(remainingMinutes)
                .startedAt(booking.getStartedAt())
                .estimatedEndTime(booking.getRemainingBookingEndTime())
                .build();

        BookingEvent event = BookingEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(BookingEvent.EventType.RESERVATION_COMPLETED)
                .timestamp(completedAt)
                .userId(booking.getUserId())
                .reservationId(booking.getBookingId())
                .data(data)
                .build();

        kafkaTemplate.send("booking.state", event.getReservationId().toString(), event);
    }

    /**
     * STOP_RESERVATION для payment-service. {@code debitSum} — сумма к дополнительному
     * списанию (полная стоимость минус уже оплаченная при старте 1 минута).
     */
    private BookingEventMessage buildStopEvent(BookingEntity booking, int totalMinutes, BigDecimal debitSum) {
        return BookingEventMessage.builder()
                .bookingId(booking.getBookingId())
                .stationId(booking.getStationId())
                .connectorId(booking.getConnectorId())
                .userId(booking.getUserId())
                .eventType(BookingEventMessage.EventType.STOP_RESERVATION)
                .totalSum(debitSum)
                .totalMinutes(totalMinutes)
                .startedAt(booking.getStartedAt())
                .endedAt(booking.getEndedAt())
                .build();
    }
}