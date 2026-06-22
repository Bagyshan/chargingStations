package charg.ing.stations.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangeRoleRequest {

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "USER|CONTRACTOR|SPECIALIST|ADMIN",
            message = "Role must be USER, CONTRACTOR, SPECIALIST, or ADMIN")
    private String role;
}