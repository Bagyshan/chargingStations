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
import org.springframework.http.HttpStatus;
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
    public Mono<ResponseEntity<ApiResponse<Object>>> verifyEmail(
            @RequestParam String token) {

        log.info("Email verification attempt with token");

        return userService.verifyEmail(token)
                .thenReturn(ResponseEntity
                        .ok(ApiResponse.success("Email verified successfully", null)))
                .doOnSuccess(response -> log.info("Email verification successful"))
                .doOnError(error -> log.warn("Email verification failed: {}", error.getMessage()));
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