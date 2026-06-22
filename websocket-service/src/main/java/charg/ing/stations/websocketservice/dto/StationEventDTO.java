package charg.ing.stations.websocketservice.dto;

import charg.ing.stations.websocketservice.dto.meter.MeterValueMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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

    private String eventId;
    private Instant timestamp;
    private EventType eventType;
    private String stationId;
    private Integer stationVersion;
    private StationStateDTO stationState;
    private MeterValueMessage meterValue;
    private Map<String, Object> metadata;
    private Object eventData;
}