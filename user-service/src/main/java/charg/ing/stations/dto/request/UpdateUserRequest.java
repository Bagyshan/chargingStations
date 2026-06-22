package charg.ing.stations.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 100, message = "First name too long")
    private String firstName;

    @Size(max = 100, message = "Last name too long")
    private String lastName;

    @Pattern(
            regexp = "^\\+?[1-9]\\d{1,14}$",
            message = "Invalid phone number format"
    )
    private String phone;
}