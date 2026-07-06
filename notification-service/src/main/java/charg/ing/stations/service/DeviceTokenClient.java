package charg.ing.stations.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Внутренний клиент к user-service за FCM-токенами устройств пользователя.
 * Авторизация — общий секрет {@code X-Internal-Token} (env INTERNAL_API_TOKEN,
 * одинаковый в обоих сервисах); вызовы идут напрямую по сервисному адресу
 * внутри сети, минуя публичный gateway.
 */
@Slf4j
@Component
public class DeviceTokenClient {

    private final WebClient webClient;

    public DeviceTokenClient(
            @Value("${services.user.base-url:http://localhost:8005}") String userServiceBaseUrl,
            @Value("${internal.api-token}") String internalApiToken) {
        this.webClient = WebClient.builder()
                .baseUrl(userServiceBaseUrl)
                .defaultHeader("X-Internal-Token", internalApiToken)
                .build();
    }

    /** Токены всех устройств пользователя (пустой список при любой ошибке). */
    public List<String> tokensForUser(String keycloakId) {
        try {
            List<String> tokens = webClient.get()
                    .uri("/api/v1/internal/device-tokens/{id}", keycloakId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                    .block(Duration.ofSeconds(5));
            return tokens == null ? List.of() : tokens;
        } catch (Exception e) {
            log.error("Failed to fetch device tokens for {}: {}", keycloakId, e.getMessage());
            return List.of();
        }
    }

    /** Удалить протухший токен (FCM UNREGISTERED). Best-effort. */
    public void deleteToken(String token) {
        try {
            webClient.delete()
                    .uri(uri -> uri.path("/api/v1/internal/device-tokens")
                            .queryParam("token", token)
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("Failed to prune device token: {}", e.getMessage());
        }
    }
}
