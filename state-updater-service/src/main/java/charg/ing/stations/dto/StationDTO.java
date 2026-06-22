package charg.ing.stations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationDTO {

    private String id;
    private String chargeBoxId;
    private Integer version;
    private Instant lastUpdated;
    private String power;
    private BigDecimal kwCost;
    private BigDecimal bookingMinuteCost;
    private GeoLocationDTO geolocation;
    private AddressDTO address;
    private List<ConnectorDTO> connectors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDTO {
        private Integer id;
        private String addressName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectorDTO {
        private Integer connectorId;
        private String status;
        private Integer version;
        private Instant lastUpdated;
        private ConnectorTypeDTO connectorType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectorTypeDTO {
        private Integer id;
        private String connectorTypeName;
        private String connectorTypeIcon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocationDTO {
        private Double latitude;
        private Double longitude;

        // Можно добавить метод для получения в формате "lat, lng"
        public String getCoordinates() {
            if (latitude != null && longitude != null) {
                return latitude + "," + longitude;
            }
            return null;
        }
    }

    public boolean hasValidCoordinates() {
        return geolocation.latitude != null && geolocation.longitude != null &&
                !Double.isNaN(geolocation.latitude) && !Double.isNaN(geolocation.longitude);
    }
}


//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//public class StationDTO {
//    private String id;
//    private String chargeBoxId;
//    private Integer version;
//    private Instant lastUpdated;
//    private GeoLocationDTO geolocation;
//    private List<ConnectorDTO> connectors;
//
//    @Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class ConnectorDTO {
//        private Integer connectorId;
//        private String status;
//        private Integer version;
//        private Instant lastUpdated;
//    }
//
//    @Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class GeoLocationDTO {
//        private Double latitude;
//        private Double longitude;
//
//        // Можно добавить метод для получения в формате "lat, lng"
//        public String getCoordinates() {
//            if (latitude != null && longitude != null) {
//                return latitude + "," + longitude;
//            }
//            return null;
//        }
//    }
//}