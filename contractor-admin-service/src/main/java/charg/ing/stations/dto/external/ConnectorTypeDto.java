package charg.ing.stations.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorTypeDto {
    private Integer id;
    private String connectorTypeName;
    private String connectorTypeIcon;
    private Integer connectorsCount;
}
