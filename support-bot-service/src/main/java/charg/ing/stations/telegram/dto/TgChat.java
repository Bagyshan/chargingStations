package charg.ing.stations.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Чат, из которого пришло сообщение (id — куда отвечать). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TgChat(
        @JsonProperty("id") long id,
        @JsonProperty("type") String type
) {
}
