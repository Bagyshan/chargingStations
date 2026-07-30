package charg.ing.stations.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Запрос на смену почты авторизованным пользователем: новый адрес + текущий
 * пароль (для подтверждения владельца). Сама почта меняется только после
 * перехода по ссылке, отправленной на новый адрес.
 */
@Data
public class ChangeEmailRequest {

    @NotBlank(message = "New email is required")
    @Email(message = "Invalid email format")
    private String newEmail;

    @NotBlank(message = "Current password is required")
    private String password;
}
