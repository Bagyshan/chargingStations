package citrine.os.station.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Map;

public class JwtUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String, Object> parseToken(String token) {
        try {
            String[] chunks = token.split("\\.");
            if (chunks.length < 2) {
                throw new IllegalArgumentException("Invalid JWT");
            }
            String payload = new String(Base64.getUrlDecoder().decode(chunks[1]));
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT", e);
        }
    }

    public static boolean isEmailVerified(Map<String, Object> claims) {
        Object verified = claims.get("email_verified");
        return verified != null && Boolean.parseBoolean(verified.toString());
    }

    public static String getUserId(Map<String, Object> claims) {
        return (String) claims.get("sub");
    }

    public static String getEmail(Map<String, Object> claims) {
        return (String) claims.get("email");
    }
}