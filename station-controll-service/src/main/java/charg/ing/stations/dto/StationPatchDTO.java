package charg.ing.stations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationPatchDTO {
    private String id;
    private String chargeBoxId;
    private String ocppProtocol;
    private String chargePointVendor;
    private String chargePointModel;
    private String chargePointSerialNumber;
    private String chargeBoxSerialNumber;
    private String firmwareVersion;
    private String iccid;
    private String imsi;
    private String meterType;
    private String meterSerialNumber;
    private String ocppTag;
    private String ownerId;
    private String power;
    private BigDecimal kwCost;
    private BigDecimal bookingMinuteCost;
    private Integer addressId;
    private GeoLocationDTO geolocation;

    @Data
    public static class GeoLocationDTO {
        /**
         * Формат: "lat,lng"
         */
        private String coordinates;

        public Double getLatitude() {
            if (coordinates == null) return null;
            return Double.parseDouble(coordinates.split(",")[0].trim());
        }

        public Double getLongitude() {
            if (coordinates == null) return null;
            return Double.parseDouble(coordinates.split(",")[1].trim());
        }
    }
}
