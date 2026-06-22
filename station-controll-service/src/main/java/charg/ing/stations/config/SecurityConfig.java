package charg.ing.stations.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter))
                )
                .authorizeExchange(exchanges -> exchanges
                        // Swagger / actuator — public
                        .pathMatchers(
                                "/webjars/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/actuator/health",
                                "/uploads/**"
                        ).permitAll()
                        // Read-only station endpoints — public (state-updater, websocket-service)
                        .pathMatchers(HttpMethod.GET, "/api/stations/**").permitAll()
                        // PATCH on stations and connectors — restricted
                        .pathMatchers(HttpMethod.PATCH, "/api/stations/**", "/api/connectors/**")
                                .hasAnyRole("ADMIN", "SPECIALIST", "CONTRACTOR")
                        // Everything else requires authentication
                        .anyExchange().authenticated()
                )
                .build();
    }
}
