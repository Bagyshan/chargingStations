package charg.ing.stations.dto.connector_type;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ConnectorTypeRequest {
    @NotBlank(message = "Name is required")
    private String connectorTypeName;

    private MultipartFile icon;

    private Integer connectorsCount;
}