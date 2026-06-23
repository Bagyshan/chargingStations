package charg.ing.stations.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Сообщение из топика {@code user.events} (продюсер — user-service).
 * Значение приходит как обычная JSON-строка через StringSerializer (без type-заголовков),
 * поэтому парсится вручную из {@code ConsumerRecord<String, String>}.
 * Нас интересует только {@code USER_REGISTERED} с {@code keycloakId} для создания кошелька.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEventMessage {
    private String eventType;
    private Long userId;
    private String keycloakId;
    private String userEmail;
    private String userRole;
}
