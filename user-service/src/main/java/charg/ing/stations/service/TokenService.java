package charg.ing.stations.service;

import charg.ing.stations.dto.response.AuthResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;

    private final WebClient webClient;

    public Mono<AuthResponse> refreshAccessToken(String refreshToken) {
        log.debug("Refreshing token with refresh token");

        return webClient.post()
                .uri(authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("refresh_token", refreshToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(10))
                .map(jsonNode -> {
                    log.info("Token refreshed successfully");

                    return AuthResponse.builder()
                            .accessToken(jsonNode.get("access_token").asText())
                            .refreshToken(jsonNode.get("refresh_token").asText())
                            .tokenType(jsonNode.get("token_type").asText())
                            .expiresIn(jsonNode.get("expires_in").longValue())
                            .scope(jsonNode.get("scope").asText())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Failed to refresh token: {}", e.getMessage());
                    return Mono.error(new RuntimeException("Token refresh failed: " + e.getMessage()));
                });
    }

    public Mono<Boolean> validateToken(String token) {
        log.debug("Validating token");

        return webClient.post()
                .uri(authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token/introspect")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("token", token)
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(5))
                .map(jsonNode -> {
                    boolean active = jsonNode.get("active").asBoolean();
                    if (active) {
                        log.debug("Token is valid");
                    } else {
                        log.debug("Token is invalid");
                    }
                    return active;
                })
                .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    public Mono<Void> logout(String refreshToken) {
        log.debug("Logging out user (invalidating refresh token)");

        return webClient.post()
                .uri(authServerUrl + "/realms/" + realm + "/protocol/openid-connect/logout")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("refresh_token", refreshToken))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(v -> log.info("User logged out successfully"))
                .doOnError(e -> log.warn("Logout failed: {}", e.getMessage()))
                .then();
    }

    public Mono<Map<String, Object>> getTokenInfo(String token) {
        return webClient.post()
                .uri(authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token/introspect")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("token", token)
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    Map<String, Object> tokenInfo = new HashMap<>();

                    if (jsonNode.has("active") && jsonNode.get("active").asBoolean()) {
                        tokenInfo.put("active", true);
                        tokenInfo.put("username", jsonNode.get("preferred_username").asText());
                        tokenInfo.put("email", jsonNode.get("email").asText());
                        tokenInfo.put("userId", jsonNode.get("sub").asText());
                        tokenInfo.put("expiresAt", jsonNode.get("exp").asLong());

                        if (jsonNode.has("realm_access") && jsonNode.get("realm_access").has("roles")) {
                            tokenInfo.put("roles", jsonNode.get("realm_access").get("roles"));
                        }
                    } else {
                        tokenInfo.put("active", false);
                    }

                    return tokenInfo;
                })
                .onErrorReturn(Map.of("active", false));
    }
}





//package charg.ing.stations.service;
//
//import charg.ing.stations.dto.response.AuthResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.keycloak.OAuth2Constants;
//import org.keycloak.admin.client.Keycloak;
//import org.keycloak.admin.client.KeycloakBuilder;
//import org.keycloak.representations.AccessTokenResponse;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Mono;
//
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class TokenService {
//
//    @Value("${keycloak.auth-server-url}")
//    private String authServerUrl;
//
//    @Value("${keycloak.realm}")
//    private String realm;
//
//    @Value("${keycloak.resource}")
//    private String clientId;
//
//    @Value("${keycloak.credentials.secret}")
//    private String clientSecret;
//
//    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
//
//    public Mono<AuthResponse> refreshAccessToken(String refreshToken) {
//        return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
//            try {
//                log.debug("Refreshing token with refresh token");
//
//                Keycloak keycloak = KeycloakBuilder.builder()
//                        .serverUrl(authServerUrl)
//                        .realm(realm)
//                        .clientId(clientId)
//                        .clientSecret(clientSecret)
//                        .grantType(OAuth2Constants.REFRESH_TOKEN)
//                        .refreshToken(refreshToken)
//                        .build();
//
//                AccessTokenResponse tokenResponse = keycloak.tokenManager().getAccessToken();
//
//                log.info("Token refreshed successfully");
//
//                return AuthResponse.builder()
//                        .accessToken(tokenResponse.getToken())
//                        .refreshToken(tokenResponse.getRefreshToken())
//                        .tokenType(tokenResponse.getTokenType())
//                        .expiresIn(tokenResponse.getExpiresIn())
//                        .scope(tokenResponse.getScope())
//                        .build();
//
//            } catch (Exception e) {
//                log.error("Failed to refresh token", e);
//                throw new RuntimeException("Token refresh failed: " + e.getMessage(), e);
//            }
//        }, executorService));
//    }
//
//    public Mono<Boolean> validateToken(String token) {
//        return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
//            try {
//                Keycloak keycloak = KeycloakBuilder.builder()
//                        .serverUrl(authServerUrl)
//                        .realm(realm)
//                        .clientId(clientId)
//                        .clientSecret(clientSecret)
//                        .build();
//
//                // Проверяем токен через introspection endpoint
//                var tokenInfo = keycloak.tokenManager().validateToken(token);
//                return tokenInfo != null && tokenInfo.getActive();
//
//            } catch (Exception e) {
//                log.warn("Token validation failed", e);
//                return false;
//            }
//        }, executorService));
//    }
//
//    public Mono<Void> logout(String refreshToken) {
//        return Mono.fromFuture(CompletableFuture.runAsync(() -> {
//            try {
//                Keycloak keycloak = KeycloakBuilder.builder()
//                        .serverUrl(authServerUrl)
//                        .realm(realm)
//                        .clientId(clientId)
//                        .clientSecret(clientSecret)
//                        .build();
//
//                // В Keycloak нет прямого метода logout через admin API
//                // Обычно logout выполняется на клиенте или через endpoint /protocol/openid-connect/logout
//                log.info("User logged out (refresh token invalidated)");
//
//            } catch (Exception e) {
//                log.error("Logout failed", e);
//            }
//        }, executorService));
//    }
//}