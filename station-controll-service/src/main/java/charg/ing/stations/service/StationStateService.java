package charg.ing.stations.service;

import charg.ing.stations.entity.AddressEntity;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.entity.ConnectorTypeEntity;
import charg.ing.stations.entity.StationStateOutbox;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.repository.StationStateOutboxRepository;
import charg.ing.stations.util.IconUrlResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class StationStateService {

    private final ChargeBoxRepository stationRepository;
    private final ConnectorRepository connectorRepository;
    private final StationStateOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final IconUrlResolver iconUrlResolver;

//    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publishStationState(String chargeBoxId, int connectorId, String connectorStatus, long connectorVersion, long chargeBoxVersion) {
        ChargeBoxEntity station = stationRepository.findByChargeBoxId(chargeBoxId);

        List<ConnectorEntity> connectors = connectorRepository.findConnectorEntitiesByChargeBoxId(chargeBoxId);

        String stationStateJson = createStationStatePayload(station, connectors, connectorId, connectorStatus, connectorVersion, chargeBoxVersion);

        StationStateOutbox outbox = StationStateOutbox.builder()
                .aggregateId(chargeBoxId)
                .aggregateType("STATION")
                .eventType("STATION_STATE_UPDATED")
                .payload(stationStateJson)
                .createdAt(Instant.now())
                .published(false)
                .build();

        outboxRepository.save(outbox);
    }

    /**
     * Публикует снимок состояния станции (для карты) без привязки к конкретному коннектору —
     * каждый коннектор отдаёт свой текущий статус. Используется при смене административного
     * статуса / online, чтобы Redis-кэш и фильтр доступности на карте были актуальны.
     * {@code chargeBoxVersion} должен быть НОВЕЕ предыдущего, иначе state-updater пропустит снимок.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publishStationSnapshot(String chargeBoxId, long chargeBoxVersion) {
        ChargeBoxEntity station = stationRepository.findByChargeBoxId(chargeBoxId);
        if (station == null) {
            return;
        }
        List<ConnectorEntity> connectors = connectorRepository.findConnectorEntitiesByChargeBoxId(chargeBoxId);
        String json = createStationStatePayload(station, connectors, -1, null, 0L, chargeBoxVersion);

        StationStateOutbox outbox = StationStateOutbox.builder()
                .aggregateId(chargeBoxId)
                .aggregateType("STATION")
                .eventType("STATION_STATE_UPDATED")
                .payload(json)
                .createdAt(Instant.now())
                .published(false)
                .build();
        outboxRepository.save(outbox);
    }

    private String createStationStatePayload(
            ChargeBoxEntity station,
            List<ConnectorEntity> connectors,
            int currentConnectorId,
            String connectorStatus,
            long connectorVersion,
            long chargeBoxVersion
    ) {

        try {
            Map<String, Object> payload = new HashMap<>();

            payload.put("id", station.getId());
            payload.put("stationId", station.getChargeBoxId());
            payload.put("lastUpdated", Instant.now().toString());
            payload.put("meterType", station.getMeterType());
            payload.put("ocppTag", station.getOcppTag());
            payload.put("version", chargeBoxVersion);
            payload.put("source", "station-service");
            payload.put("power", station.getPower());
            payload.put("kwCost", station.getKwCost());
            payload.put("bookingMinuteCost", station.getBookingMinuteCost());
            payload.put("serviceStatus", station.getServiceStatus() != null ? station.getServiceStatus().name() : null);
            payload.put("online", station.getOnline());

            List<Map<String, Object>> connectorStates = connectors.stream()
                    .map((ConnectorEntity connector) -> createConnectorState(connector, currentConnectorId, connectorStatus, connectorVersion))
                    .collect(Collectors.toList());

            payload.put("connectors", connectorStates);

            Map <String, Object> stationGeo = createGeolocationPayload(station.getLatitude(), station.getLongitude());

            payload.put("geolocation", stationGeo);
            payload.put("address", createAddressPayload(station.getAddress()));

            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create station state payload", e);
        }
    }

    private Map<String, Object> createConnectorState(
            ConnectorEntity connector,
            Integer currentConnectorId,
            String connectorStatus,
            Long connectorVersion
    ) {
        Map<String, Object> state = new HashMap<>();

        state.put("connectorId", connector.getConnectorId());
        if (currentConnectorId.equals(connector.getConnectorId())) {
            state.put("status", connectorStatus);
            state.put("version", connectorVersion);
        } else {
            state.put("status", connector.getStatus());
            state.put("version", connector.getVersion());
        }
        state.put("connectorType", createConnectorTypePayload(connector.getConnectorType()));
        return state;
    }

    private Map<String, Object> createGeolocationPayload(
            Object lat,
            Object lng
    ) {
        Map<String, Object> state = new HashMap<>();
        state.put("lat", lat);
        state.put("lng", lng);
        return state;
    }

    private Map<String, Object> createAddressPayload(AddressEntity address) {
        if (address == null) {
            return null;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", address.getId());
        m.put("addressName", address.getAddressName());
        return m;
    }

    private Map<String, Object> createConnectorTypePayload(ConnectorTypeEntity connectorType) {
        if (connectorType == null) {
            return null;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", connectorType.getId());
        m.put("connectorTypeName", connectorType.getConnectorTypeName());
        m.put("connectorTypeIcon", iconUrlResolver.resolve(connectorType.getConnectorTypeIcon()));
        return m;
    }
}