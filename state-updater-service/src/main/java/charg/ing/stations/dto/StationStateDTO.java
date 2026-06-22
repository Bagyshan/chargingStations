package charg.ing.stations.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StationStateDTO {
//
//    @JsonProperty("stationId")
//    private String stationId;
//
//    @JsonProperty("chargeBoxId")
//    private String chargeBoxId;
//
//    @JsonProperty("lastUpdated")
//    private Instant lastUpdated;
//
//    @JsonProperty("connectors")
//    private List<ConnectorDTO> connectors;
//
//    @JsonProperty("meterType")
//    private String meterType;
//
//    @JsonProperty("source")
//    private String source;
//
//    @JsonProperty("version")
//    private Integer version;
//
//    // Дополнительные поля, которые могут приходить из station-service
//    private String stationStatus;
//    private Double lat;
//    private Double lon;
//    private String name;
//
//
//    @Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class ConnectorDTO {
//        @JsonProperty("connectorId")
//        private Integer connectorId;
//
//        @JsonProperty("version")
//        private Integer version;
//
//        @JsonProperty("status")
//        private String status;
//
//        @JsonProperty("lastUpdated")
//        private Instant lastUpdated;
//
//        // Опциональные поля
//        private String sessionId;
//        private Double meterValue;
//    }

    @JsonProperty("lastUpdated")
    private Instant lastUpdated;

    @JsonProperty("connectors")
    private List<ConnectorState> connectors;

//    @JsonProperty("lng")
//    private Double longitude;
//
//    @JsonProperty("lat")
//    private Double latitude;

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

    @JsonProperty("address")
    private Address address;

    @JsonProperty("power")
    private String power;

    @JsonProperty("kwCost")
    private BigDecimal kwCost;

    @JsonProperty("bookingMinuteCost")
    private BigDecimal bookingMinuteCost;

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

        @JsonProperty("connectorType")
        private ConnectorType connectorType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        @JsonProperty("id")
        private Integer id;

        @JsonProperty("addressName")
        private String addressName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectorType {
        @JsonProperty("id")
        private Integer id;

        @JsonProperty("connectorTypeName")
        private String connectorTypeName;

        @JsonProperty("connectorTypeIcon")
        private String connectorTypeIcon;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geolocation {

        @JsonProperty("lat")
        private Double latitude;

        @JsonProperty("lng")
        private Double longitude;

        // Можно добавить метод для получения в формате "lat, lng"
        public String getCoordinates() {
            if (latitude != null && longitude != null) {
                return latitude + "," + longitude;
            }
            return null;
        }
    }

    public static StationStateDTO fromStationDTO(StationDTO stationDTO) {
        StationStateDTO dto = new StationStateDTO();
        dto.setStationId(stationDTO.getChargeBoxId());
        dto.setId(Integer.parseInt(stationDTO.getId()));
        dto.setVersion(stationDTO.getVersion());
        dto.setLastUpdated(stationDTO.getLastUpdated());
        dto.setSource("station-control-service");
        dto.setPower(stationDTO.getPower());
        dto.setKwCost(stationDTO.getKwCost());
        dto.setBookingMinuteCost(stationDTO.getBookingMinuteCost());

        if (stationDTO.getGeolocation() != null) {
            StationStateDTO.Geolocation geoLocationDTO = new StationStateDTO.Geolocation();
            geoLocationDTO.setLatitude(stationDTO.getGeolocation().getLatitude());
            geoLocationDTO.setLongitude(stationDTO.getGeolocation().getLongitude());
            dto.setGeolocation(geoLocationDTO);
        }

        if (stationDTO.getAddress() != null) {
            dto.setAddress(new Address(
                    stationDTO.getAddress().getId(),
                    stationDTO.getAddress().getAddressName()
            ));
        }

        if (stationDTO.getConnectors() != null) {
            dto.setConnectors(stationDTO.getConnectors().stream()
                    .map(connector -> {
                        ConnectorState cs = new ConnectorState();
                        cs.setConnectorId(connector.getConnectorId());
                        cs.setVersion(connector.getVersion());
                        cs.setStatus(connector.getStatus());
                        if (connector.getConnectorType() != null) {
                            cs.setConnectorType(new ConnectorType(
                                    connector.getConnectorType().getId(),
                                    connector.getConnectorType().getConnectorTypeName(),
                                    connector.getConnectorType().getConnectorTypeIcon()
                            ));
                        }
                        return cs;
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}
