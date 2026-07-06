package charg.ing.stations.controller;

import charg.ing.stations.dto.response.ApiResponse;
import charg.ing.stations.repository.DeviceTokenRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * FCM-токены устройств для push-уведомлений.
 *
 * <p>Пользовательские endpoint'ы (JWT): регистрация токена при логине/обновлении и
 * удаление при выходе. Внутренние endpoint'ы (заголовок {@code X-Internal-Token},
 * доступны notification-service внутри сети): получение токенов пользователя для
 * отправки пуша и удаление протухших токенов (FCM UNREGISTERED).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Device tokens", description = "FCM-токены устройств для push-уведомлений")
public class DeviceTokenController {

    private final DeviceTokenRepository repository;

    @Value("${internal.api-token}")
    private String internalApiToken;

    public record RegisterDeviceTokenRequest(@NotBlank String token, String platform) {}

    @Operation(summary = "Зарегистрировать/обновить FCM-токен устройства текущего пользователя")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/users/device-tokens")
    public Mono<ResponseEntity<ApiResponse<Object>>> register(
            @Valid @RequestBody RegisterDeviceTokenRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String platform = request.platform() == null || request.platform().isBlank()
                ? "android" : request.platform();
        return repository.upsert(keycloakId, request.token(), platform)
                .doOnSuccess(n -> log.info("Device token registered for keycloakId={}", keycloakId))
                .thenReturn(ResponseEntity.ok(ApiResponse.success("Device token registered")));
    }

    @Operation(summary = "Удалить FCM-токен устройства (выход из аккаунта)")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/v1/users/device-tokens")
    public Mono<ResponseEntity<ApiResponse<Object>>> unregister(
            @RequestParam("token") String token,
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        return repository.deleteByKeycloakIdAndToken(keycloakId, token)
                .doOnSuccess(v -> log.info("Device token removed for keycloakId={}", keycloakId))
                .thenReturn(ResponseEntity.ok(ApiResponse.success("Device token removed")));
    }

    // ── Внутренние endpoint'ы для notification-service ──────────────────────

    @Operation(summary = "[internal] Токены устройств пользователя")
    @GetMapping("/api/v1/internal/device-tokens/{keycloakId}")
    public Mono<ResponseEntity<List<String>>> tokensForUser(
            @PathVariable String keycloakId,
            @RequestHeader(value = "X-Internal-Token", required = false) String header) {
        if (!internalApiToken.equals(header)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return repository.findByKeycloakId(keycloakId)
                .map(t -> t.getToken())
                .collectList()
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "[internal] Удалить протухший токен (FCM UNREGISTERED)")
    @DeleteMapping("/api/v1/internal/device-tokens")
    public Mono<ResponseEntity<Void>> deleteStaleToken(
            @RequestParam("token") String token,
            @RequestHeader(value = "X-Internal-Token", required = false) String header) {
        if (!internalApiToken.equals(header)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return repository.deleteByToken(token)
                .doOnSuccess(v -> log.info("Stale device token pruned"))
                .thenReturn(ResponseEntity.noContent().build());
    }
}
