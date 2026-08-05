package charg.ing.stations.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Тело запроса на повторную отправку письма подтверждения (без авторизации). */
@Data
public class ResendVerificationRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
