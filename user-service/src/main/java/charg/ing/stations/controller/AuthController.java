package charg.ing.stations.controller;

import charg.ing.stations.dto.request.*;
import charg.ing.stations.dto.response.ApiResponse;
import charg.ing.stations.dto.response.AuthResponse;
import charg.ing.stations.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API для аутентификации и авторизации")
public class AuthController {

    private final UserService userService;

    @Operation(summary = "Регистрация нового пользователя")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Пользователь успешно зарегистрирован",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Пользователь уже существует")
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        log.info("Registration request for email: {}, IP: {}", request.getEmail(), ipAddress);

        return userService.register(request, ipAddress, userAgent)
                .map(authResponse -> ResponseEntity
                        .created(URI.create("/api/v1/users/profile"))
                        .body(ApiResponse.success("User registered successfully", authResponse)))
                .doOnSuccess(response -> log.info("Registration successful for: {}", request.getEmail()))
                .doOnError(error -> log.error("Registration failed for: {}", request.getEmail(), error));
    }

    @Operation(summary = "Вход в систему")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Успешный вход",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Неверные учетные данные")
    })
    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        log.info("Login attempt for email: {}, IP: {}", request.getEmail(), ipAddress);

        return userService.login(request.getEmail(), request.getPassword(), ipAddress, userAgent)
                .map(authResponse -> ResponseEntity
                        .ok(ApiResponse.success("Login successful", authResponse)))
                .doOnSuccess(response -> log.info("Login successful for: {}", request.getEmail()))
                .doOnError(error -> log.warn("Login failed for: {}", request.getEmail(), error));
    }

    @Operation(summary = "Обновление access token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Token успешно обновлен"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Недействительный refresh token")
    })
    @PostMapping("/refresh")
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        log.info("Token refresh request");

        return userService.refreshToken(request.getRefreshToken())
                .map(authResponse -> ResponseEntity
                        .ok(ApiResponse.success("Token refreshed successfully", authResponse)))
                .doOnSuccess(response -> log.info("Token refreshed successfully"))
                .doOnError(error -> log.warn("Token refresh failed: {}", error.getMessage()));
    }

    @Operation(summary = "Выход из системы")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Успешный выход")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<Void>>> logout(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {

        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : "";

        log.info("Logout request");

        // В реальном приложении можно добавить токен в blacklist или вызвать logout в Keycloak
        return Mono.just(ResponseEntity
                .ok(ApiResponse.success("Logout successful")));
    }

    @Operation(summary = "Запрос сброса пароля")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Запрос на сброс пароля принят"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден")
    })
    @PostMapping("/password/reset-request")
    public Mono<ResponseEntity<ApiResponse<Object>>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {

        log.info("Password reset request for email: {}", request.getEmail());

        return userService.initiatePasswordReset(request.getEmail())
                .thenReturn(ResponseEntity
                        .ok(ApiResponse.success("Password reset instructions sent to email", null)))
                .doOnSuccess(response -> log.info("Password reset requested for: {}", request.getEmail()))
                .doOnError(error -> log.warn("Password reset request failed for: {}", request.getEmail()));
    }

    @Operation(summary = "Подтверждение сброса пароля")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Пароль успешно сброшен"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Недействительный или просроченный токен")
    })
    @PostMapping("/password/reset")
    public Mono<ResponseEntity<ApiResponse<Object>>> resetPassword(
            @Valid @RequestBody PasswordResetConfirmRequest request) {

        log.info("Password reset confirmation for token");

        return userService.resetPassword(request.getToken(), request.getNewPassword())
                .thenReturn(ResponseEntity
                        .ok(ApiResponse.success("Password reset successfully", null)))
                .doOnSuccess(response -> log.info("Password reset successful"))
                .doOnError(error -> log.warn("Password reset failed: {}", error.getMessage()));
    }

    @Operation(summary = "Подтверждение email")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Email успешно подтвержден"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Недействительный или просроченный токен")
    })
    @GetMapping("/verify-email")
    public Mono<ResponseEntity<Object>> verifyEmail(
            @RequestParam String token,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {

        // Content negotiation: браузер (Universal Link открылся в Safari, т.к. приложение
        // не установлено) получает брендовую HTML-страницу; приложение (Dio, Accept: json)
        // — обычный JSON. Ссылка одна и та же — bat-energy.com.kg/user/.../verify-email.
        final boolean wantsHtml = accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
        log.info("Email verification attempt (html={})", wantsHtml);

        return userService.verifyEmail(token)
                .then(Mono.fromSupplier(() -> verificationResult(wantsHtml, true)))
                .doOnSuccess(r -> log.info("Email verification successful"))
                .onErrorResume(error -> {
                    log.warn("Email verification failed: {}", error.getMessage());
                    return Mono.just(verificationResult(wantsHtml, false));
                });
    }

    /** Формирует ответ подтверждения: HTML-страница для браузера или JSON для приложения. */
    private ResponseEntity<Object> verificationResult(boolean html, boolean ok) {
        final HttpStatus status = ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        if (html) {
            final Object body = ok ? VERIFIED_HTML : VERIFY_ERROR_HTML;
            return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(body);
        }
        final Object body = ok
                ? ApiResponse.success("Email verified successfully", null)
                : ApiResponse.error("Invalid or expired verification token");
        return ResponseEntity.status(status).body(body);
    }

    // Брендовые страницы подтверждения (показываются в браузере, если приложение не
    // установлено; при установленном приложении Universal Link открывает само приложение).
    private static final String VERIFIED_HTML = """
            <!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Почта подтверждена — BatEnergy</title>
            <style>
              :root{color-scheme:light dark}*{box-sizing:border-box}
              body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
                font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif;
                background:#f5f6f8;color:#1c1d21;padding:24px}
              .card{max-width:420px;width:100%;background:#fff;border:1px solid #ececf0;border-radius:22px;
                padding:32px 26px;text-align:center;box-shadow:0 12px 40px rgba(90,46,92,.10)}
              .badge{width:84px;height:84px;margin:0 auto 20px;border-radius:26px;display:flex;
                align-items:center;justify-content:center;font-size:44px;color:#fff;
                background-image:linear-gradient(135deg,#FFB43A,#FFA20D,#8E4368,#5A2E5C)}
              h1{font-size:22px;margin:0 0 10px}p{font-size:15px;line-height:1.5;color:#5b5f68;margin:0}
              .brand{margin-top:22px;font-weight:800;letter-spacing:.5px;color:#8E4368}
              @media (prefers-color-scheme:dark){body{background:#161519;color:#f4f3f7}
                .card{background:#1f1e24;border-color:#34323b}p{color:#b7b4c2}}
            </style></head><body><div class="card">
              <div class="badge">✓</div>
              <h1>Почта подтверждена</h1>
              <p>Ваш адрес успешно подтверждён. Вернитесь в приложение <b>BatEnergy</b> и войдите в аккаунт.</p>
              <div class="brand">BatEnergy</div>
            </div></body></html>""";

    private static final String VERIFY_ERROR_HTML = """
            <!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Ссылка недействительна — BatEnergy</title>
            <style>
              :root{color-scheme:light dark}*{box-sizing:border-box}
              body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
                font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif;
                background:#f5f6f8;color:#1c1d21;padding:24px}
              .card{max-width:420px;width:100%;background:#fff;border:1px solid #ececf0;border-radius:22px;
                padding:32px 26px;text-align:center;box-shadow:0 12px 40px rgba(90,46,92,.10)}
              .badge{width:84px;height:84px;margin:0 auto 20px;border-radius:26px;display:flex;
                align-items:center;justify-content:center;font-size:44px;color:#fff;background:#E5484D}
              h1{font-size:22px;margin:0 0 10px}p{font-size:15px;line-height:1.5;color:#5b5f68;margin:0}
              .brand{margin-top:22px;font-weight:800;letter-spacing:.5px;color:#8E4368}
              @media (prefers-color-scheme:dark){body{background:#161519;color:#f4f3f7}
                .card{background:#1f1e24;border-color:#34323b}p{color:#b7b4c2}}
            </style></head><body><div class="card">
              <div class="badge">!</div>
              <h1>Ссылка недействительна</h1>
              <p>Ссылка устарела или уже была использована. Откройте приложение <b>BatEnergy</b>
              и запросите новое письмо для подтверждения.</p>
              <div class="brand">BatEnergy</div>
            </div></body></html>""";

    @Operation(summary = "Подтверждение смены email (ссылка из письма на новый адрес)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Email успешно изменён"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Недействительный или просроченный токен")
    })
    @GetMapping("/confirm-email-change")
    public Mono<ResponseEntity<ApiResponse<Object>>> confirmEmailChange(
            @RequestParam String token) {

        log.info("Email change confirmation attempt with token");

        return userService.confirmEmailChange(token)
                .thenReturn(ResponseEntity
                        .ok(ApiResponse.success("Email changed successfully", null)))
                .doOnSuccess(response -> log.info("Email change confirmed successfully"))
                .doOnError(error -> log.warn("Email change confirmation failed: {}", error.getMessage()));
    }

    @Operation(summary = "Запрос повторной отправки email для подтверждения")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Email отправлен"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/verify-email/request")
    public Mono<ResponseEntity<ApiResponse<Void>>> requestEmailVerification(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {

        // Извлекаем email из JWT токена
        // В реальном приложении нужно парсить JWT и извлекать email/subject
        log.info("Email verification re-request");

        return Mono.just(ResponseEntity
                .ok(ApiResponse.success("Verification email sent")));
    }

    @Operation(summary = "Повторная отправка письма подтверждения по email (без авторизации)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Если аккаунт существует и не подтверждён — письмо отправлено")
    })
    @PostMapping("/verify-email/resend")
    public Mono<ResponseEntity<ApiResponse<Void>>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {

        final String email = request.getEmail().trim().toLowerCase();
        log.info("Public resend verification request for: {}", email);

        // ВСЕГДА возвращаем 200 с одинаковым сообщением — не раскрываем, существует
        // ли аккаунт и подтверждён ли он (защита от перебора email). Ошибки (в т.ч.
        // «пользователь не найден») подавляем и логируем.
        final ApiResponse<Void> ok = ApiResponse.success(
                "Если аккаунт существует и ещё не подтверждён, письмо отправлено");
        return userService.initiateEmailVerification(email)
                .thenReturn(ResponseEntity.ok(ok))
                .onErrorResume(error -> {
                    log.warn("Resend verification suppressed for {}: {}", email, error.toString());
                    return Mono.just(ResponseEntity.ok(ok));
                });
    }

    @Operation(summary = "Проверка доступности email")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Email доступен/недоступен")
    })
    @GetMapping("/check-email")
    public Mono<ResponseEntity<ApiResponse<Boolean>>> checkEmailAvailability(
            @RequestParam String email) {

        log.debug("Checking email availability: {}", email);

        return userService.checkEmailExists(email)
                .map(exists -> ResponseEntity
                        .ok(ApiResponse.success(
                                exists ? "Email already exists" : "Email available",
                                !exists)))
                .doOnSuccess(response -> log.debug("Email check completed: {}", email));
    }
}