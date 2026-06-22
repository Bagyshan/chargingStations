package charg.ing.stations.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class JwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String principal = jwt.getClaimAsString("sub");
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, principal));
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Можно извлечь роли из realm_access или resource_access
        return Collections.emptyList(); // для простоты без ролей
    }
}