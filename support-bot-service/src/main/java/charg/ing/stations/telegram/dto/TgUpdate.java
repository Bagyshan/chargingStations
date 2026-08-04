package charg.ing.stations.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Одно обновление Telegram. Нас интересует только входящее сообщение. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TgUpdate(
        @JsonProperty("update_id") long updateId,
        @JsonProperty("message") TgMessage message
) {
}
