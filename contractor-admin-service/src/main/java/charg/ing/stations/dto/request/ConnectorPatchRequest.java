package charg.ing.stations.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorPatchRequest {
    private String info;
    private String vendorId;
    private Integer connectorTypeId;
}
