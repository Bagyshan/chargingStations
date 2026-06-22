package charg.ing.stations.dto.connector_type;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectorTypeResponse {
    private Integer id;
    private String connectorTypeName;
    private String connectorTypeIcon;
    private Integer connectorsCount;
}