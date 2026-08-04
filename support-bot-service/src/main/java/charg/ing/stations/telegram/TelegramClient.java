package charg.ing.stations.telegram;

import charg.ing.stations.config.TelegramBotProperties;
import charg.ing.stations.telegram.dto.GetUpdatesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

/**
 * Тонкая обёртка над Telegram Bot API (getUpdates + sendMessage) на WebClient.
 * Реализуем сами, чтобы не тянуть тяжёлую библиотеку и остаться в реактивном стеке.
 */
@Slf4j
@Component
public class TelegramClient {

    private final WebClient webClient;

    public TelegramClient(TelegramBotProperties props, WebClient.Builder builder) {
        // Response timeout должен быть больше poll-таймаута, иначе long polling будет
        // рваться раньше, чем Telegram успеет ответить.
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(props.getPollTimeoutSeconds() + 15L));

        this.webClient = builder
                .baseUrl(props.getApiBase() + "/bot" + (props.getToken() == null ? "" : props.getToken()))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** Long polling: ждёт до timeoutSeconds новых апдейтов начиная с offset. */
    public Mono<GetUpdatesResponse> getUpdates(long offset, int timeoutSeconds) {
        return webClient.get()
                .uri(uri -> uri.path("/getUpdates")
                        .queryParam("offset", offset)
                        .queryParam("timeout", timeoutSeconds)
                        .queryParam("allowed_updates", "[\"message\"]")
                        .build())
                .retrieve()
                .bodyToMono(GetUpdatesResponse.class);
    }

    /**
     * Отправить текст в чат. chatId — Object: long для пользователя, String для
     * чата поддержки (id группы/канала). Telegram принимает оба варианта.
     * Текст отправляем без parse_mode — простой текст, без риска инъекций разметки.
     */
    public Mono<Void> sendMessage(Object chatId, String text) {
        return webClient.post()
                .uri("/sendMessage")
                .bodyValue(Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "disable_web_page_preview", true))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("sendMessage to {} failed: {}", chatId, e.toString()))
                .then();
    }
}
