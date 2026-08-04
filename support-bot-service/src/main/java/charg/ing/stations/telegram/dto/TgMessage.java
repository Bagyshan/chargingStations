package charg.ing.stations.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Сообщение Telegram (минимально необходимый набор полей). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TgMessage(
        @JsonProperty("message_id") long messageId,
        @JsonProperty("from") TgUser from,
        @JsonProperty("chat") TgChat chat,
        @JsonProperty("text") String text
) {
}
