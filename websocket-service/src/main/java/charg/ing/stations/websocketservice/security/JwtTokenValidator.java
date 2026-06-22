package charg.ing.stations.websocketservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class JwtTokenValidator {
    private final ReactiveJwtDecoder jwtDecoder;

    public JwtTokenValidator(ReactiveJwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public Mono<String> validateTokenAndGetUserId(String token) {
        log.debug("Validating token: {}", token.substring(0, Math.min(20, token.length())) + "...");
        return jwtDecoder.decode(token)
                .doOnNext(jwt -> log.debug("Token valid, subject: {}", jwt.getSubject()))
                .map(jwt -> jwt.getSubject())
                .onErrorResume(e -> {
                    log.warn("Token validation failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}