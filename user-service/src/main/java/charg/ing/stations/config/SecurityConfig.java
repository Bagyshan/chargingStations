package charg.ing.stations.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    // Берём из конфигурации (env SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI),
    // чтобы в Docker/деплое JWKS тянулся с keycloak:8080, а не с захардкоженного localhost.
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://localhost:8080/realms/charging-stations/protocol/openid-connect/certs}")
    private String jwkSetUri;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        // Публичные endpoints
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/password/reset-request").permitAll()
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/auth/verify-email",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/actuator/health").permitAll()

                        // Защищенные endpoints
                        .pathMatchers("/api/v1/auth/logout").authenticated()
                        .pathMatchers("/api/v1/users/profile").authenticated()
                        .pathMatchers("/api/v1/favorites/**").authenticated()
                        .pathMatchers("/api/v1/users/all").hasAnyRole("ADMIN", "SPECIALIST", "CONTRACTOR")
                        .pathMatchers("/api/v1/users/**").hasAnyRole("USER", "CONTRACTOR", "SPECIALIST")
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/contractor/**").hasRole("CONTRACTOR")
                        .pathMatchers("/api/v1/specialist/**").hasRole("SPECIALIST")

                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)
                        )
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> Mono.fromRunnable(() -> {
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                        }))
                        .accessDeniedHandler((exchange, denied) -> Mono.fromRunnable(() -> {
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                        }))
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*")); // В продакшене укажите конкретные домены
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();
    }
}