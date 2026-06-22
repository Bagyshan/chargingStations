package charg.ing.stations.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class JwtUtils {

    private static final String CLIENT_ID = "user-service";

    private JwtUtils() {}

    public static String getUserId(Jwt jwt) {
        return jwt.getSubject();
    }

    public static boolean isAdminOrSpecialist(Jwt jwt) {
        List<String> roles = extractRoles(jwt);
        return roles.contains("ADMIN") || roles.contains("SPECIALIST");
    }

    public static boolean isContractor(Jwt jwt) {
        List<String> roles = extractRoles(jwt);
        return roles.contains("CONTRACTOR") && !roles.contains("ADMIN") && !roles.contains("SPECIALIST");
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractRoles(Jwt jwt) {
        List<String> roles = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object realmRoles = realmAccess.get("roles");
            if (realmRoles instanceof Collection<?> list) {
                list.stream().map(Object::toString).forEach(roles::add);
            }
        }

        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null && resourceAccess.containsKey(CLIENT_ID)) {
            Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(CLIENT_ID);
            if (clientAccess != null) {
                Object clientRoles = clientAccess.get("roles");
                if (clientRoles instanceof Collection<?> list) {
                    list.stream().map(Object::toString).forEach(roles::add);
                }
            }
        }

        return roles;
    }
}
