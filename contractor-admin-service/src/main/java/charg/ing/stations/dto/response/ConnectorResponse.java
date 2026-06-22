package charg.ing.stations.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Connector information")
public class ConnectorResponse {

    @Schema(description = "Database primary key")
    private Integer id;

    @Schema(description = "OCPP charge box identifier", example = "CP_001")
    private String chargeBoxId;

    @Schema(description = "Connector number within the charge box", example = "1")
    private Integer connectorId;

    private String info;
    private Instant createdAt;
    private String vendorId;

    @Schema(description = "Current connector status",
            example = "Available",
            allowableValues = {"Available", "Charging", "Reserved", "Unavailable", "Faulted"})
    private String status;

    private Integer connectorTypeId;
}
