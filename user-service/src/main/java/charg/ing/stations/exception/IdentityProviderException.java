package charg.ing.stations.exception;

import org.springframework.http.HttpStatus;

/**
 * Ошибка операции в Keycloak (identity provider). Несёт HTTP-статус, под которым
 * её нужно отдать клиенту: например, нарушение политики паролей при регистрации —
 * это {@link HttpStatus#BAD_REQUEST} с реальным сообщением Keycloak, а недоступность
 * провайдера — {@link HttpStatus#BAD_GATEWAY}.
 */
public class IdentityProviderException extends RuntimeException {

    private final HttpStatus status;

    public IdentityProviderException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
