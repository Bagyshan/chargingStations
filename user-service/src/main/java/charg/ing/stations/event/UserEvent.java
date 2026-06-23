package charg.ing.stations.event;

import charg.ing.stations.event.enums.UserEventType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserEvent {
    private UserEventType eventType;
    private Long userId;
    private String keycloakId;
    private String userEmail;
    private String userRole;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private String ipAddress;
    private String userAgent;
    private Map<String, Object> metadata;

    // Статические методы-фабрики для удобства
    public static UserEvent userRegistered(Long userId, String email, String role) {
        return UserEvent.builder()
                .eventType(UserEventType.USER_REGISTERED)
                .userId(userId)
                .userEmail(email)
                .userRole(role)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static UserEvent emailVerificationRequested(Long userId, String email, String token) {
        return UserEvent.builder()
                .eventType(UserEventType.EMAIL_VERIFICATION_REQUESTED)
                .userId(userId)
                .userEmail(email)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of("token", token))
                .build();
    }

    public static UserEvent passwordResetRequested(Long userId, String email, String token) {
        return UserEvent.builder()
                .eventType(UserEventType.PASSWORD_RESET_REQUESTED)
                .userId(userId)
                .userEmail(email)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of("token", token))
                .build();
    }
}