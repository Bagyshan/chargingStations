package charg.ing.stations.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Декодер JWT для resource-server, у которого JWK-URI берётся из конфигурации и может
 * обновляться на лету.
 *
 * <p>Источник URI (по приоритету):
 * <ol>
 *   <li>{@code app.security.jwk-set-uri} — может прийти из Consul KV (hot-reload);</li>
 *   <li>стандартный {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} —
 *       совместимость: прод задаёт его через env {@code SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI},
 *       локаль (профиль consul) — в application-consul.yaml.</li>
 * </ol>
 *
 * <p>{@code @RefreshScope}: при {@code POST /actuator/refresh} или срабатывании Consul KV watch
 * бин пересоздаётся с новым URI — Keycloak можно переключить без перезапуска сервиса. Наличие
 * этого бина отключает автоконфиг-декодер ({@code @ConditionalOnMissingBean}), а Spring Security
 * сам подхватывает {@link ReactiveJwtDecoder} в {@code oauth2ResourceServer().jwt()}.
 */
@Configuration
public class JwtDecoderConfig {

    /** Дефолт на случай, когда ни KV, ни стандартное свойство/ENV не заданы (напр. профиль local). */
    private static final String DEFAULT_JWK_URI =
            "http://localhost:8080/realms/charging-stations/protocol/openid-connect/certs";

    @Bean
    @RefreshScope
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${app.security.jwk-set-uri:${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}}")
            String jwkSetUri) {
        String uri = StringUtils.hasText(jwkSetUri) ? jwkSetUri : DEFAULT_JWK_URI;
        return NimbusReactiveJwtDecoder.withJwkSetUri(uri).build();
    }
}
