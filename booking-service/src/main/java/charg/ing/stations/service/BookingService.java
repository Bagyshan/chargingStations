package charg.ing.stations.service;

import charg.ing.stations.dto.event.BookingEvent;
import charg.ing.stations.dto.event.BookingEventMessage;
import charg.ing.stations.dto.kafka.PaymentRequest;
import charg.ing.stations.dto.kafka.PaymentResponse;
import charg.ing.stations.dto.kafka.StationRequest;
import charg.ing.stations.dto.kafka.StationResponse;
import charg.ing.stations.dto.request.BookingRequest;
import charg.ing.stations.dto.responses.BookingHistoryResponse;
import charg.ing.stations.dto.responses.BookingResponse;
import charg.ing.stations.entity.BookingEntity;
import charg.ing.stations.repository.BookingRepository;
import charg.ing.stations.producer.BookingEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Duration KAFKA_TIMEOUT = Duration.ofSeconds(7);

    private final KafkaRequestReplyService kafkaRequestReplyService;
    private final BookingRepository bookingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BookingEventProducer bookingEventProducer;

    /**
     * История бронирований пользователя (все статусы), новые — первыми.
     */
    public Flux<BookingHistoryResponse> getUserBookings(UUID userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toHistory);
    }

    private BookingHistoryResponse toHistory(BookingEntity b) {
        return BookingHistoryResponse.builder()
                .bookingId(b.getBookingId())
                .stationId(b.getStationId())
                .connectorId(b.getConnectorId())
                .status(b.getStatus())
                .pricePerMinute(b.getPricePerMinute())
                .maxBookingMinutes(b.getMaxBookingMinutes())
                .totalMinutes(b.getTotalMinutes())
                .totalSum(b.getTotalSum())
                .startedAt(b.getStartedAt())
                .endedAt(b.getEndedAt())
                .createdAt(b.getCreatedAt())
                .build();
    }

    public Mono<BookingResponse> createBooking(UUID userId, BookingRequest request) {
        UUID paymentRequestId = UUID.randomUUID();
        UUID stationRequestId = UUID.randomUUID();

        // Отправляем запрос в payment-service
        PaymentRequest paymentRequest = new PaymentRequest(paymentRequestId, userId);
        Mono<PaymentResponse> paymentResponseMono = kafkaRequestReplyService.sendAndReceive(
                "booking.payment.requests",
                "booking.payment.responses",
                paymentRequest,
                paymentRequestId,
                PaymentResponse.class,
                KAFKA_TIMEOUT
        );

        // Отправляем запрос в station-service
        StationRequest stationRequest = new StationRequest(stationRequestId, request.stationId(), request.connectorId());
        Mono<StationResponse> stationResponseMono = kafkaRequestReplyService.sendAndReceive(
                "booking.station.requests",
                "booking.station.responses",
                stationRequest,
                stationRequestId,
                StationResponse.class,
                KAFKA_TIMEOUT
        );

        // Ждём оба ответа
        return Mono.zip(paymentResponseMono, stationResponseMono)
                .flatMap(tuple -> {
                    PaymentResponse paymentResp = tuple.getT1();
                    StationResponse stationResp = tuple.getT2();

                    // Проверяем успешность
                    if (!paymentResp.isSuccess() || !stationResp.isAvailable()) {
                        log.warn("Booking failed: payment success={}, station available={}",
                                paymentResp.isSuccess(), stationResp.isAvailable());
                        return createFailedBooking(userId, request, paymentResp, stationResp);
                    }

                    BigDecimal balance = paymentResp.getBalance();
                    BigDecimal pricePerMinute = stationResp.getPricePerMinute();

                    // Вычисляем максимальное количество минут
                    int maxMinutes = balance.divide(pricePerMinute, RoundingMode.DOWN).intValue();
                    if (maxMinutes <= 0) {
                        log.warn("Insufficient balance: balance={}, pricePerMinute={}", balance, pricePerMinute);
                        return createFailedBooking(userId, request, paymentResp, stationResp);
                    }

                    Instant now = Instant.now();
                    Instant remainingBookingEndTime = now.plusSeconds(maxMinutes * 60L);

                    BookingEntity booking = BookingEntity.builder()
                            .userId(userId)
                            .stationId(request.stationId())
                            .connectorId(request.connectorId())
                            .pricePerMinute(pricePerMinute)
                            .maxBookingMinutes(maxMinutes)
                            .currentBookingMinutes(0)
                            .startedAt(now)
//                            .endedAt(endedAt)
                            .remainingBookingEndTime(remainingBookingEndTime)
                            .status("ACTIVE")
                            .createdAt(now)
                            .build();

                    BookingEventMessage event = buildStartEvent(booking);
//                    bookingEventProducer.sendBookingEvent(event);

                    return bookingRepository.save(booking)
                            .flatMap(saved ->
                                    bookingEventProducer.sendBookingEvent(event)
                                            .thenReturn(saved)
                            )
                            .doOnSuccess(this::sendCreatedEvent)
                            .map(saved -> new BookingResponse(
                                    saved.getBookingId(),
                                    saved.getStatus(),
                                    saved.getMaxBookingMinutes(),
                                    saved.getStartedAt(),
                                    saved.getRemainingBookingEndTime(),
                                    saved.getEndedAt(),
                                    null
                            ));
                })
                .onErrorResume(e -> {
                    log.error("Error during booking creation", e);
                    String errorMessage;
                    if (e instanceof TimeoutException) {
                        errorMessage = "Timeout waiting for response from services";
                    } else {
                        errorMessage = "Internal server error: " + e.getMessage();
                    }
                    // Сохраняем FAILED-запись без ответов от сервисов
                    BookingEntity failedBooking = BookingEntity.builder()
                            .userId(userId)
                            .stationId(request.stationId())
                            .connectorId(request.connectorId())
                            .pricePerMinute(BigDecimal.ZERO)
                            .maxBookingMinutes(0)
                            .currentBookingMinutes(0)
                            .status("FAILED")
                            .createdAt(Instant.now())
                            .build();
                    return bookingRepository.save(failedBooking)
                            .map(saved -> BookingResponse.builder()
                                    .bookingId(saved.getBookingId())
                                    .status(saved.getStatus())
                                    .errorMessage(errorMessage)
                                    .build());
                });
//                .onErrorResume(e -> {
//                    log.error("Error during booking creation", e);
//                    return createFailedBooking(userId, request, null, null);
//                });
    }

    private Mono<BookingResponse> createFailedBooking(UUID userId, BookingRequest request,
                                                      PaymentResponse paymentResp, StationResponse stationResp) {
        BigDecimal price = BigDecimal.ZERO;
        if (stationResp != null && stationResp.getPricePerMinute() != null) {
            price = stationResp.getPricePerMinute();
        }

        // Формируем сообщение об ошибке
        StringBuilder errorMsg = new StringBuilder();
        if (paymentResp != null && !paymentResp.isSuccess()) {
            errorMsg.append("Payment error: ").append(paymentResp.getErrorMessage()).append("; ");
        }
        if (stationResp != null && !stationResp.isAvailable()) {
            errorMsg.append("Station error: ").append(stationResp.getErrorMessage()).append("; ");
        }
        if (errorMsg.length() == 0) {
            errorMsg.append("Unknown error");
        }

        BookingEntity failedBooking = BookingEntity.builder()
                .userId(userId)
                .stationId(request.stationId())
                .connectorId(request.connectorId())
                .pricePerMinute(price)
                .maxBookingMinutes(0)
                .currentBookingMinutes(0)
                .status("FAILED")
                .createdAt(Instant.now())
                .build();

        return bookingRepository.save(failedBooking)
                .map(saved -> BookingResponse.builder()
                        .bookingId(saved.getBookingId())
                        .status(saved.getStatus())
                        .maxBookingMinutes(saved.getMaxBookingMinutes())
                        .startedAt(saved.getStartedAt())
                        .remainingBookingEndTime(saved.getRemainingBookingEndTime())
                        .endedAt(saved.getEndedAt())
                        .errorMessage(errorMsg.toString())
                        .build());
//        return bookingRepository.save(failedBooking)
//                .map(saved -> new BookingResponse(
//                        saved.getBookingId(),
//                        saved.getStatus(),
//                        saved.getMaxBookingMinutes(),
//                        saved.getStartedAt(),
//                        saved.getRemainingBookingEndTime(),
//                        saved.getEndedAt()
//                ));
    }

    private void sendCreatedEvent(BookingEntity booking) {
        BookingEvent.EventData data = BookingEvent.EventData.builder()
                .stationId(booking.getStationId())
                .connectorId(booking.getConnectorId())
                .pricePerMinute(booking.getPricePerMinute())
                .maxBookingMinutes(booking.getMaxBookingMinutes())
                .startedAt(booking.getStartedAt())
                .estimatedEndTime(booking.getEndedAt())
                .build();
        BookingEvent event = BookingEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(BookingEvent.EventType.RESERVATION_CREATED)
                .timestamp(booking.getStartedAt())
                .userId(booking.getUserId())
                .reservationId(booking.getBookingId())
                .data(data)
                .build();
        kafkaTemplate.send("booking.state", event.getReservationId().toString(), event);
    }

    private BookingEventMessage buildStartEvent(BookingEntity booking) {
//        BigDecimal totalSum = booking.getPricePerMinute()
//                .multiply(BigDecimal.valueOf(booking.getMaxBookingMinutes()));
        return BookingEventMessage.builder()
                .bookingId(booking.getBookingId())
                .stationId(booking.getStationId())
                .connectorId(booking.getConnectorId())
                .userId(booking.getUserId())
                .eventType(BookingEventMessage.EventType.START_RESERVATION)
//                .totalSum(totalSum)
//                .totalMinutes(booking.getMaxBookingMinutes())
                .startedAt(booking.getStartedAt())
                .endedAt(booking.getEndedAt())
                .build();
    }
}