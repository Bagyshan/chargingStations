package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StationCreateEventMessage {
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
    private String actionType;
    private Long createdAt;
}
