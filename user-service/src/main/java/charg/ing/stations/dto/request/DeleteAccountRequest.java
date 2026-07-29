package charg.ing.stations.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Удаление собственного аккаунта авторизованным пользователем.
 * Требует текущий пароль для повторной аутентификации — защита от случайного/
 * несанкционированного удаления с чужого разблокированного устройства.
 */
@Data
public class DeleteAccountRequest {
    @NotBlank(message = "Password is required")
    private String password;
}
