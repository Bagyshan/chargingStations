package charg.ing.stations.service;

import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.entity.StationStateOutbox;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.repository.StationStateOutboxRepository;
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

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
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

            payload.put("stationId", station.getChargeBoxId());
            payload.put("lastUpdated", Instant.now().toString());
            payload.put("meterType", station.getMeterType());
            payload.put("version", chargeBoxVersion);
            payload.put("source", "station-service");

            List<Map<String, Object>> connectorStates = connectors.stream()
                    .map((ConnectorEntity connector) -> createConnectorState(connector, currentConnectorId, connectorStatus, connectorVersion))
                    .collect(Collectors.toList());

            payload.put("connectors", connectorStates);

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
        return state;
    }
}