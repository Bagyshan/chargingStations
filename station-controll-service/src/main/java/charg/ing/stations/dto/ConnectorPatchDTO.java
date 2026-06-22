package charg.ing.stations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorPatchDTO {
    private String info;
    private String vendorId;
    private Integer connectorTypeId;
}
