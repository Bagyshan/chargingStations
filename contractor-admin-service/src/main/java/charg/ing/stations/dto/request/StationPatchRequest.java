package charg.ing.stations.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationPatchRequest {
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocationDTO {
        private String coordinates;
    }
}
