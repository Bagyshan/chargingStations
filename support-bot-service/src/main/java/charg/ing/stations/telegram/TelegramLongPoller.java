package charg.ing.stations.telegram;

import charg.ing.stations.bot.ComplaintBotHandler;
import charg.ing.stations.config.TelegramBotProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Бесконечный цикл long polling. После готовности приложения запрашивает getUpdates,
 * последовательно (concatMap) прогоняет апдейты через обработчик, продвигает offset и
 * повторяет. Ошибки сети не роняют цикл — пауза 3с и повтор.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramLongPoller {

    private final TelegramClient client;
    private final ComplaintBotHandler handler;
    private final TelegramBotProperties props;

    /** Смещение следующего апдейта. 0 = все неподтверждённые (Telegram сам дедуплицирует). */
    private final AtomicLong offset = new AtomicLong(0);
    private volatile Disposable subscription;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!props.isEnabled()) {
            log.warn("Telegram bot DISABLED: TELEGRAM_BOT_TOKEN не задан — long polling не запущен.");
            return;
        }
        if (!props.hasSupportChat()) {
            log.warn("TELEGRAM_SUPPORT_CHAT_ID не задан — жалобы будут только сохраняться в БД, без форварда.");
        }
        log.info("Telegram long polling запущен (poll timeout {}s).", props.getPollTimeoutSeconds());

        subscription = Flux.defer(() -> client.getUpdates(offset.get(), props.getPollTimeoutSeconds()))
                .flatMapIterable(resp -> resp.result() == null ? List.<charg.ing.stations.telegram.dto.TgUpdate>of() : resp.result())
                .concatMap(update -> handler.handle(update)
                        .onErrorResume(e -> {
                            log.error("Ошибка обработки апдейта {}: {}", update.updateId(), e.toString(), e);
                            return Mono.empty();
                        })
                        .thenReturn(update.updateId()))
                .doOnNext(id -> offset.set(id + 1))
                .then()
                .onErrorResume(e -> {
                    log.warn("getUpdates не удался, повтор через 3с: {}", e.toString());
                    return Mono.delay(Duration.ofSeconds(3)).then();
                })
                .repeat()
                .subscribe();
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Telegram long polling остановлен.");
        }
    }
}
