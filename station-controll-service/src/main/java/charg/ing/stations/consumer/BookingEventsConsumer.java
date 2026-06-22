package charg.ing.stations.consumer;

import charg.ing.stations.dto.event.BookingEventMessage;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;



//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class BookingEventsConsumer {
//
//    private final ConnectorRepository connectorRepository;
//    private final ObjectMapper objectMapper;
//
//    @KafkaListener(topics = "booking.events", groupId = "station-controller-service-group")
//    @Transactional
//    public void handleBookingEvent(Map<String, Object> payload) {
//        log.info("Received booking event: {}", payload);
//
//        BookingEventMessage event = mapToBookingEvent(payload);
//
//        processEvent(event);
//    }
//
//
//    private void processEvent(BookingEventMessage event) {
//
//        Optional<ConnectorEntity> connectorOpt =
//                connectorRepository.findByChargeBoxIdAndConnectorId(
//                        event.getStationId(), event.getConnectorId());
//
//        if (connectorOpt.isEmpty()) {
//            log.error("Connector not found: {}-{}", event.getStationId(), event.getConnectorId());
//            return;
//        }
//
//        ConnectorEntity connector = connectorOpt.get();
//
//        if (event.getEventType() == BookingEventMessage.EventType.START_RESERVATION) {
//            connector.setBookingUserId(event.getUserId().toString());
//        } else if (event.getEventType() == BookingEventMessage.EventType.STOP_RESERVATION) {
//            connector.setBookingUserId(null);
//        }
//
//        connectorRepository.save(connector);
//    }
//
//    private BookingEventMessage mapToBookingEvent(Map<String, Object> payload) {
//
//        if (payload.get("startedAt") instanceof Number start) {
//            payload.put("startedAt",
//                    Instant.ofEpochSecond(start.longValue()));
//        }
//
//        if (payload.get("endedAt") instanceof Number end) {
//            payload.put("endedAt",
//                    Instant.ofEpochSecond(end.longValue()));
//        }
//
//        return objectMapper.convertValue(payload, BookingEventMessage.class);
//    }
//}


@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventsConsumer {

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "booking.events", groupId = "station-controller-service-group")
    public void handleBookingEvent(Map<String, Object> payload, Acknowledgment ack) {

        log.info("Received booking event payload: {}", payload);

        BookingEventMessage event = mapToBookingEvent(payload);
        // Исключение пробрасывается наружу, контейнер Kafka обработает повтор (если настроен) или отправит в DLT
        reservationService.processReservationEvent(event, ack);
    }

    private BookingEventMessage mapToBookingEvent(Map<String, Object> payload) {

        // корректная конвертация timestamp если пришёл epoch
        if (payload.get("startedAt") instanceof Number start) {
            payload.put("startedAt", Instant.ofEpochSecond(start.longValue()));
        }

        if (payload.get("endedAt") instanceof Number end) {
            payload.put("endedAt", Instant.ofEpochSecond(end.longValue()));
        }

        return objectMapper.convertValue(payload, BookingEventMessage.class);
    }
}