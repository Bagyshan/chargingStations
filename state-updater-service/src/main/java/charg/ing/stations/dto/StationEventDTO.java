package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StationEventDTO {

    public enum EventType {
        STATION_CREATED,
        STATION_UPDATED,
        STATION_DELETED,
        CONNECTOR_STATUS_CHANGED,
        LOCATION_UPDATED,
        VERSION_INCREMENTED,
        METER_VALUE,
        TARIFF_UPDATED
    }

    @Builder.Default
    private String eventId = java.util.UUID.randomUUID().toString();

    @Builder.Default
    private Instant timestamp = Instant.now();

    private EventType eventType;
    private String stationId;
    private Integer stationVersion;
    private StationStateDTO stationState;
    private Map<String, Object> metadata;
    private Object eventData;

    // Конструкторы для ваших DTO
    public static StationEventDTO createStationCreatedEvent(StationStateDTO stationState) {
        return StationEventDTO.builder()
                .eventType(EventType.STATION_CREATED)
                .stationId(stationState.getStationId())
                .stationVersion(stationState.getVersion())
                .stationState(stationState)
                .metadata(Map.of(
                        "source", stationState.getSource(),
                        "connectorsCount", stationState.getConnectors() != null ?
                                stationState.getConnectors().size() : 0
                ))
                .build();
    }

    public static StationEventDTO createStationUpdatedEvent(StationStateDTO stationState,
                                                            Integer oldVersion) {
        return StationEventDTO.builder()
                .eventType(EventType.STATION_UPDATED)
                .stationId(stationState.getStationId())
                .stationVersion(stationState.getVersion())
                .stationState(stationState)
                .metadata(Map.of(
                        "oldVersion", oldVersion,
                        "versionDelta", stationState.getVersion() - oldVersion,
                        "source", stationState.getSource()
                ))
                .build();
    }

    public static StationEventDTO createStationDeletedEvent(String stationId) {
        return StationEventDTO.builder()
                .eventType(EventType.STATION_DELETED)
                .stationId(stationId)
                .metadata(Map.of(
                        "timestamp", Instant.now().toString()
                ))
                .build();
    }

    public static StationEventDTO createConnectorStatusChangedEvent(
            String stationId,
            Integer connectorId,
            String oldStatus,
            String newStatus,
            Integer version) {

        return StationEventDTO.builder()
                .eventType(EventType.CONNECTOR_STATUS_CHANGED)
                .stationId(stationId)
                .stationVersion(version)
                .metadata(Map.of(
                        "connectorId", connectorId,
                        "oldStatus", oldStatus,
                        "newStatus", newStatus
                ))
                .build();
    }

    public static StationEventDTO createLocationUpdatedEvent(StationStateDTO stationState) {
        return StationEventDTO.builder()
                .eventType(EventType.LOCATION_UPDATED)
                .stationId(stationState.getStationId())
                .stationVersion(stationState.getVersion())
                .stationState(stationState)
                .metadata(Map.of(
                        "latitude", stationState.getGeolocation() != null ?
                                stationState.getGeolocation().getLatitude() : null,
                        "longitude", stationState.getGeolocation() != null ?
                                stationState.getGeolocation().getLongitude() : null,
                        "source", stationState.getSource()
                ))
                .build();
    }

    public static StationEventDTO createTariffsBatchUpdatedEvent(List<StationHourlyTariffEvent> tariffs) {
        int hour = tariffs.isEmpty() ? -1 : tariffs.get(0).getHour();
        return StationEventDTO.builder()
                .eventType(EventType.TARIFF_UPDATED)
                .stationId("BATCH")
                .metadata(Map.of(
                        "hour", hour,
                        "stationsCount", tariffs.size()
                ))
                .eventData(tariffs)
                .build();
    }

    // NEW: фабричный метод для meter value
    public static StationEventDTO createMeterValueEvent(String chargeBoxId, MeterValueMessage meterData) {
        return StationEventDTO.builder()
                .eventType(EventType.METER_VALUE)
                .stationId(chargeBoxId)               // используем stationId для идентификации станции
                .eventData(meterData)                  // полное исходное сообщение
                .timestamp(Instant.ofEpochMilli(meterData.getTimestamp()))
                .build();
    }
}