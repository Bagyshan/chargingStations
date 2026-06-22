package charg.ing.stations.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for creating or updating a charge box")
public class ChargeBoxRequest {

    @Schema(description = "OCPP charge box identifier", example = "CP_001")
    private String chargeBoxId;

    @Schema(description = "OCPP protocol version", example = "ocpp1.6")
    private String ocppProtocol;

    @Schema(example = "Solidstudio")
    private String chargePointVendor;

    @Schema(example = "VirtualChargePoint")
    private String chargePointModel;

    private String chargePointSerialNumber;
    private String chargeBoxSerialNumber;
    private String firmwareVersion;
    private String iccid;
    private String imsi;
    private String meterType;
    private String meterSerialNumber;

    @Schema(description = "OCPP tag (RFID) associated with this box")
    private String ocppTag;

    @Schema(description = "Owner/contractor identifier")
    private String ownerId;

    @Schema(description = "Power output, e.g. 7kW, 50kW")
    private String power;

    @Schema(description = "Cost per kWh", example = "15.00")
    private BigDecimal kwCost;

    @Schema(description = "Booking cost per minute", example = "2.50")
    private BigDecimal bookingMinuteCost;

    @Schema(description = "Foreign key to address table")
    private Integer addressId;

    @Schema(description = "Station latitude coordinate", example = "42.8746")
    private Double latitude;

    @Schema(description = "Station longitude coordinate", example = "74.5698")
    private Double longitude;
}
