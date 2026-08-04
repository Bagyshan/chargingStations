package charg.ing.stations.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Автор сообщения. Используем id/username/first_name для привязки жалобы. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TgUser(
        @JsonProperty("id") long id,
        @JsonProperty("is_bot") boolean isBot,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("username") String username
) {
}
