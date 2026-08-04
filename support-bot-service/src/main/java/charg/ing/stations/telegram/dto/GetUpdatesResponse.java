package charg.ing.stations.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Ответ Telegram Bot API на getUpdates. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GetUpdatesResponse(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("result") List<TgUpdate> result
) {
}
