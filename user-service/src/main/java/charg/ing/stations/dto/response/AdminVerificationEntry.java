package charg.ing.stations.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Запись обзора подтверждения email для админки: сам токен (OTP-код), полная
 * ссылка подтверждения, срок и статус. Позволяет админу увидеть OTP пользователя
 * и при необходимости активировать аккаунт вручную.
 */
@Data
@Builder
public class AdminVerificationEntry {
    private Long userId;
    private String email;
    private Boolean emailVerified;
    private String token;
    private String verifyLink;
    private LocalDateTime expiresAt;
    private Boolean used;
    private Boolean expired;
}
