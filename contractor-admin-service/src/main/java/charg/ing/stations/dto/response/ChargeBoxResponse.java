package charg.ing.stations.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Charge box information")
public class ChargeBoxResponse {

    @Schema(description = "Database primary key")
    private Integer id;

    @Schema(description = "OCPP charge box identifier", example = "CP_001")
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
    private Instant createdAt;
    private String ownerId;
    private String power;
    private BigDecimal kwCost;
    private BigDecimal bookingMinuteCost;
    private Integer addressId;
}
