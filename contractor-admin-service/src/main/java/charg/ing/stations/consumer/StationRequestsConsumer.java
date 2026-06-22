package charg.ing.stations.consumer;

import charg.ing.stations.config.KafkaReceiverFactory;
import charg.ing.stations.dto.ConnectorCreateEventMessage;
import charg.ing.stations.dto.StationCreateEventMessage;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class StationRequestsConsumer {

    private final KafkaReceiverFactory receiverFactory;
    private final ChargeBoxRepository chargeBoxRepository;
    private final ConnectorRepository connectorRepository;
    private final ObjectMapper objectMapper;

    private Disposable subscription;

    @EventListener(ApplicationReadyEvent.class)
    public void startConsuming() {
        log.info("Starting StationRequestsConsumer for topic station.requests");
        subscription = KafkaReceiver.create(
                receiverFactory.create("contractor-admin-station-consumer", Set.of("station.requests"))
        )
        .receive()
        .flatMap(record -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) record.value();
            String actionType = (String) payload.get("actionType");

            Mono<Void> processing = switch (actionType != null ? actionType : "") {
                case "CHARGE_BOX" -> handleChargeBox(
                        objectMapper.convertValue(payload, StationCreateEventMessage.class));
                case "CONNECTOR" -> handleConnector(
                        objectMapper.convertValue(payload, ConnectorCreateEventMessage.class));
                default -> Mono.empty();
            };

            return processing
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())
                    .onErrorResume(DataAccessException.class, e -> {
                        log.error("DB error processing station.requests actionType={} — offset NOT committed, consumer will restart", actionType, e);
                        return Mono.error(e);
                    })
                    .onErrorResume(e -> {
                        log.error("Business error processing station.requests actionType={} — skipping", actionType, e);
                        record.receiverOffset().acknowledge();
                        return Mono.empty();
                    });
        })
        .subscribe(
                v -> {},
                e -> log.error("Fatal error in station.requests consumer", e)
        );
    }

    private Mono<Void> handleChargeBox(StationCreateEventMessage event) {
        return chargeBoxRepository.findByChargeBoxId(event.getChargeBoxId())
                .defaultIfEmpty(new ChargeBoxEntity())
                .flatMap(entity -> applyChargeBox(entity, event))
                .onErrorResume(DuplicateKeyException.class, e ->
                        chargeBoxRepository.findByChargeBoxId(event.getChargeBoxId())
                                .flatMap(entity -> applyChargeBox(entity, event))
                )
                .doOnSuccess(e -> log.info("Saved ChargeBox chargeBoxId={}", e.getChargeBoxId()))
                .then();
    }

    private Mono<ChargeBoxEntity> applyChargeBox(ChargeBoxEntity entity, StationCreateEventMessage event) {
        entity.setChargeBoxId(event.getChargeBoxId());
        entity.setOcppProtocol(event.getOcppProtocol());
        entity.setChargePointVendor(event.getChargePointVendor());
        entity.setChargePointModel(event.getChargePointModel());
        entity.setChargePointSerialNumber(event.getChargePointSerialNumber());
        entity.setChargeBoxSerialNumber(event.getChargeBoxSerialNumber());
        entity.setFirmwareVersion(event.getFirmwareVersion());
        entity.setIccid(event.getIccid());
        entity.setImsi(event.getImsi());
        entity.setMeterType(event.getMeterType());
        entity.setMeterSerialNumber(event.getMeterSerialNumber());
        if (entity.getCreatedAt() == null && event.getCreatedAt() != null) {
            entity.setCreatedAt(Instant.ofEpochMilli(event.getCreatedAt()));
        }
        return chargeBoxRepository.save(entity);
    }

    private Mono<Void> handleConnector(ConnectorCreateEventMessage event) {
        return connectorRepository.findByChargeBoxIdAndConnectorId(event.getChargeBoxId(), event.getConnectorId())
                .defaultIfEmpty(new ConnectorEntity())
                .flatMap(entity -> applyConnector(entity, event))
                .onErrorResume(DuplicateKeyException.class, e ->
                        connectorRepository.findByChargeBoxIdAndConnectorId(event.getChargeBoxId(), event.getConnectorId())
                                .flatMap(entity -> applyConnector(entity, event))
                )
                .doOnSuccess(e -> log.info("Saved Connector chargeBoxId={} connectorId={}", e.getChargeBoxId(), e.getConnectorId()))
                .then();
    }

    private Mono<ConnectorEntity> applyConnector(ConnectorEntity entity, ConnectorCreateEventMessage event) {
        entity.setChargeBoxId(event.getChargeBoxId());
        entity.setConnectorId(event.getConnectorId());
        entity.setInfo(event.getInfo());
        entity.setVendorId(event.getVendorId());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        return connectorRepository.save(entity);
    }
}
