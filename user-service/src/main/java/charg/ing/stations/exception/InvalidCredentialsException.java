package charg.ing.stations.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Неверная пара email/пароль при входе. Возвращается единообразно и для
 * несуществующего пользователя, и для неверного пароля — чтобы не раскрывать
 * факт наличия аккаунта.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
