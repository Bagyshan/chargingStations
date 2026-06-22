package charg.ing.stations.websocketservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StationStateDTO {

    @JsonProperty("lastUpdated")
    private Instant lastUpdated;

    @JsonProperty("connectors")
    private List<ConnectorState> connectors;

    @JsonProperty("meterType")
    private String meterType;

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("source")
    private String source;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("stationId")
    private String stationId;

    @JsonProperty("serviceStatus")
    private String serviceStatus;

    @JsonProperty("online")
    private Boolean online;

    @JsonProperty("geolocation")
    private Geolocation geolocation;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectorState {
        @JsonProperty("connectorId")
        private Integer connectorId;

        @JsonProperty("version")
        private Integer version;

        @JsonProperty("status")
        private String status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geolocation {
        @JsonProperty("lat")
        private Double latitude;

        @JsonProperty("lng")
        private Double longitude;

        @JsonProperty("coordinates")
        private String coordinates;
    }
}