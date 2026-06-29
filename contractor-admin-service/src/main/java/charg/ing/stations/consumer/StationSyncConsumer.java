package charg.ing.stations.consumer;

import charg.ing.stations.config.KafkaReceiverFactory;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Держит динамические данные станций/коннекторов в contractor-admin актуальными — зеркалит
 * station-controll. Подписан на те же события, что и station-controll: {@code station.state}
 * (service_status, цены, координаты, статусы коннекторов), {@code station.status} (operational-статус
 * коннектора), {@code station.connectivity} (online/last_seen_at). Только ОБНОВЛЯЕТ уже существующие
 * строки (создание идёт через {@code station.requests} в {@link StationRequestsConsumer}) — так нет
 * гонок вставки между двумя консьюмерами.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StationSyncConsumer {

    private final KafkaReceiverFactory receiverFactory;
    private final ChargeBoxRepository chargeBoxRepository;
    private final ConnectorRepository connectorRepository;

    private Disposable subscription;

    @EventListener(ApplicationReadyEvent.class)
    public void startConsuming() {
        Set<String> topics = Set.of("station.state", "station.status", "station.connectivity");
        log.info("Starting StationSyncConsumer for topics {}", topics);
        subscription = KafkaReceiver.create(
                        receiverFactory.create("contractor-admin-station-sync", topics))
                .receive()
                .flatMap(record -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = record.value() instanceof Map
                            ? (Map<String, Object>) record.value() : Collections.emptyMap();

                    Mono<Void> processing = switch (record.topic()) {
                        case "station.state" -> handleStationState(payload);
                        case "station.status" -> handleConnectorStatus(payload);
                        case "station.connectivity" -> handleConnectivity(payload);
                        default -> Mono.empty();
                    };

                    return processing
                            .doOnSuccess(v -> record.receiverOffset().acknowledge())
                            .onErrorResume(DataAccessException.class, e -> {
                                log.error("DB error on {} — offset NOT committed, will retry", record.topic(), e);
                                return Mono.error(e);
                            })
                            .onErrorResume(e -> {
                                log.error("Business error on {} — skipping", record.topic(), e);
                                record.receiverOffset().acknowledge();
                                return Mono.empty();
                            });
                })
                .subscribe(v -> {}, e -> log.error("Fatal error in station sync consumer", e));
    }

    // ---- station.state: service_status, цены, координаты + статусы коннекторов -----------------

    private Mono<Void> handleStationState(Map<String, Object> payload) {
        String chargeBoxId = str(payload.get("stationId"));
        if (chargeBoxId == null) {
            return Mono.empty();
        }
        Mono<Void> box = chargeBoxRepository.findByChargeBoxId(chargeBoxId)
                .flatMap(entity -> {
                    if (payload.containsKey("serviceStatus")) entity.setServiceStatus(str(payload.get("serviceStatus")));
                    if (payload.get("ocppTag") != null) entity.setOcppTag(str(payload.get("ocppTag")));
                    if (payload.get("power") != null) entity.setPower(str(payload.get("power")));
                    if (payload.get("kwCost") != null) entity.setKwCost(decimal(payload.get("kwCost")));
                    if (payload.get("bookingMinuteCost") != null) entity.setBookingMinuteCost(decimal(payload.get("bookingMinuteCost")));
                    if (payload.get("version") != null) entity.setVersion(lng(payload.get("version")));
                    Object geo = payload.get("geolocation");
                    if (geo instanceof Map<?, ?> g) {
                        if (g.get("lat") != null) entity.setLatitude(dbl(g.get("lat")));
                        if (g.get("lng") != null) entity.setLongitude(dbl(g.get("lng")));
                    }
                    // online НЕ трогаем здесь — он ведётся из station.connectivity (свежее).
                    return chargeBoxRepository.save(entity).then();
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("station.state for unknown charge_box {} — skip (ждём station.requests)", chargeBoxId)));

        Object connectorsObj = payload.get("connectors");
        Flux<Void> connectors = Flux.empty();
        if (connectorsObj instanceof List<?> list) {
            connectors = Flux.fromIterable(list)
                    .filter(Map.class::isInstance)
                    .flatMap(c -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cm = (Map<String, Object>) c;
                        Integer connectorId = intg(cm.get("connectorId"));
                        if (connectorId == null) return Mono.empty();
                        return updateConnector(chargeBoxId, connectorId, str(cm.get("status")), lng(cm.get("version")));
                    });
        }

        return box.thenMany(connectors).then();
    }

    // ---- station.status: operational-статус одного коннектора ----------------------------------

    private Mono<Void> handleConnectorStatus(Map<String, Object> payload) {
        String chargeBoxId = str(payload.get("chargeBoxId"));
        Integer connectorId = intg(payload.get("connectorId"));
        String status = str(payload.get("status"));
        if (chargeBoxId == null || connectorId == null || status == null) {
            return Mono.empty();
        }
        return updateConnector(chargeBoxId, connectorId, status, null);
    }

    // ---- station.connectivity: online / last_seen_at -------------------------------------------

    private Mono<Void> handleConnectivity(Map<String, Object> payload) {
        String chargeBoxId = str(payload.get("chargeBoxId"));
        String eventType = str(payload.get("eventType"));
        if (chargeBoxId == null || eventType == null) {
            return Mono.empty();
        }
        boolean online = !"DISCONNECTED".equalsIgnoreCase(eventType);
        return chargeBoxRepository.findByChargeBoxId(chargeBoxId)
                .flatMap(entity -> {
                    entity.setOnline(online);
                    entity.setLastSeenAt(Instant.now());
                    return chargeBoxRepository.save(entity).then();
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("connectivity for unknown charge_box {} — skip", chargeBoxId)))
                .then();
    }

    private Mono<Void> updateConnector(String chargeBoxId, Integer connectorId, String status, Long version) {
        return connectorRepository.findByChargeBoxIdAndConnectorId(chargeBoxId, connectorId)
                .flatMap(entity -> {
                    if (status != null) entity.setStatus(status);
                    if (version != null) entity.setVersion(version);
                    return connectorRepository.save(entity).then();
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("status for unknown connector {}:{} — skip", chargeBoxId, connectorId)))
                .then();
    }

    // ---- безопасные конвертеры из Map (значения могут быть Number/String) ----------------------

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private static BigDecimal decimal(Object o) {
        try {
            return o != null ? new BigDecimal(o.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long lng(Object o) {
        try {
            return o != null ? Long.parseLong(o.toString().trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intg(Object o) {
        try {
            return o != null ? Integer.parseInt(o.toString().trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double dbl(Object o) {
        try {
            return o != null ? Double.parseDouble(o.toString().trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
