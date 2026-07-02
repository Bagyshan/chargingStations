package charg.ing.stations.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Текущий пароль не совпал при смене пароля. 400 (а не 401), чтобы клиент не
 * запускал авто-refresh токена и не разлогинивал пользователя.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
