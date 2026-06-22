package charg.ing.stations.consumer;

import charg.ing.stations.service.StationConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Слушает {@code station.connectivity} (форвард из station-steve): держит признак online станции
 * актуальным для offline-детекта. Payload типизирован как {@code Map} (Kafka gotcha — иначе
 * прилетит raw ConsumerRecord).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StationConnectivityConsumer {

    private final StationConnectivityService connectivityService;

    @KafkaListener(topics = "station.connectivity", groupId = "station-controller-service-group-connectivity")
    public void onConnectivity(Map<String, Object> message, Acknowledgment ack) {
        try {
            Object chargeBoxIdObj = message.get("chargeBoxId");
            Object eventTypeObj = message.get("eventType");
            if (chargeBoxIdObj == null || eventTypeObj == null) {
                return;
            }
            connectivityService.recordConnectivity(
                    chargeBoxIdObj.toString(), eventTypeObj.toString(), parseTimestamp(message.get("timestamp")));
        } catch (Exception e) {
            log.error("Failed to process connectivity event: {}", e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }

    /** SteVe шлёт Joda DateTime (JSON-строка ISO); если распарсить не вышло — используем now(). */
    private Instant parseTimestamp(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw.toString());
        } catch (Exception ignored) {
            return null;
        }
    }
}
