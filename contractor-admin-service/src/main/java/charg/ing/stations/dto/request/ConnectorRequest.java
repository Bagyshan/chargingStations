package charg.ing.stations.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for creating or updating a connector")
public class ConnectorRequest {

    @Schema(description = "OCPP charge box identifier this connector belongs to", example = "CP_001")
    private String chargeBoxId;

    @Schema(description = "Connector number within the charge box", example = "1")
    private Integer connectorId;

    private String info;
    private String vendorId;

    @Schema(description = "Connector status", example = "Available",
            allowableValues = {"Available", "Charging", "Reserved", "Unavailable", "Faulted"})
    private String status;

    @Schema(description = "Foreign key to connector_type table")
    private Integer connectorTypeId;
}
