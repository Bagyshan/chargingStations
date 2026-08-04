package charg.ing.stations.bot;

import charg.ing.stations.complaint.ComplaintEntity;
import charg.ing.stations.complaint.ComplaintService;
import charg.ing.stations.config.TelegramBotProperties;
import charg.ing.stations.telegram.TelegramClient;
import charg.ing.stations.telegram.dto.TgMessage;
import charg.ing.stations.telegram.dto.TgUpdate;
import charg.ing.stations.telegram.dto.TgUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Диалоговый обработчик бота поддержки.
 * Флоу: /start → «опишите проблему» → «оставьте контакт (или /skip)» → сохранение
 * жалобы в БД + форвард в чат поддержки + благодарность пользователю.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintBotHandler {

    private final TelegramClient client;
    private final ComplaintService complaintService;
    private final TelegramBotProperties props;

    /** Активные диалоги по chatId. Апдейты обрабатываются последовательно (concatMap). */
    private final Map<Long, BotSession> sessions = new ConcurrentHashMap<>();

    private static final String WELCOME = """
            👋 Здравствуйте! Это поддержка BatEnergy.

            Опишите, пожалуйста, вашу проблему или пожелание одним сообщением — \
            что случилось, на какой станции и когда.

            Чтобы отменить — команда /cancel.""";

    private static final String ASK_CONTACT = """
            Спасибо! Оставьте, пожалуйста, контакт для связи (телефон или email), \
            если хотите, чтобы мы ответили.

            Или отправьте /skip, чтобы пропустить.""";

    private static final String THANKS = """
            ✅ Спасибо! Ваше обращение принято.

            Мы рассмотрим его и свяжемся с вами при необходимости. \
            Чтобы отправить ещё одно — /start.""";

    private static final String CANCELLED = "Обращение отменено. Чтобы начать заново — /start.";

    private static final String PLEASE_DESCRIBE = "Пожалуйста, опишите проблему обычным текстовым сообщением.";

    public Mono<Void> handle(TgUpdate update) {
        TgMessage msg = update.message();
        if (msg == null || msg.chat() == null || msg.text() == null) {
            return Mono.empty(); // игнорируем не-текстовые апдейты
        }
        long chatId = msg.chat().id();
        String text = msg.text().trim();

        if (text.equalsIgnoreCase("/cancel")) {
            sessions.remove(chatId);
            return client.sendMessage(chatId, CANCELLED);
        }
        if (text.equalsIgnoreCase("/start")) {
            return startFlow(chatId);
        }

        BotSession session = sessions.get(chatId);
        if (session == null) {
            // Нет активного диалога — начинаем как со /start.
            return startFlow(chatId);
        }

        return switch (session.getState()) {
            case AWAIT_DESCRIPTION -> onDescription(chatId, session, text);
            case AWAIT_CONTACT -> onContact(chatId, session, msg.from(), text);
        };
    }

    private Mono<Void> startFlow(long chatId) {
        sessions.put(chatId, new BotSession(ConversationState.AWAIT_DESCRIPTION));
        return client.sendMessage(chatId, WELCOME);
    }

    private Mono<Void> onDescription(long chatId, BotSession session, String text) {
        if (text.startsWith("/")) {
            return client.sendMessage(chatId, PLEASE_DESCRIBE);
        }
        session.setDescription(text);
        session.setState(ConversationState.AWAIT_CONTACT);
        return client.sendMessage(chatId, ASK_CONTACT);
    }

    private Mono<Void> onContact(long chatId, BotSession session, TgUser from, String text) {
        String contact = (text.equalsIgnoreCase("/skip") || text.equals("-") || text.isBlank())
                ? null : text;
        sessions.remove(chatId);

        ComplaintEntity complaint = ComplaintEntity.builder()
                .telegramUserId(from != null ? from.id() : null)
                .telegramUsername(from != null ? from.username() : null)
                .telegramName(from != null ? from.firstName() : null)
                .contact(contact)
                .message(session.getDescription())
                .status("NEW")
                .createdAt(Instant.now())
                .build();

        return complaintService.save(complaint)
                .doOnNext(saved -> log.info("Сохранена жалоба #{} от tg {}", saved.getId(), saved.getTelegramUserId()))
                .flatMap(saved -> forwardToSupport(saved).thenReturn(saved))
                .flatMap(saved -> client.sendMessage(chatId, THANKS))
                .then();
    }

    /** Форвард жалобы в чат поддержки, если он настроен. */
    private Mono<Void> forwardToSupport(ComplaintEntity c) {
        if (!props.hasSupportChat()) {
            return Mono.empty();
        }
        return client.sendMessage(props.getSupportChatId(), buildSupportMessage(c))
                .onErrorResume(e -> {
                    log.error("Не удалось форварднуть жалобу #{} в чат поддержки: {}", c.getId(), e.toString());
                    return Mono.empty();
                });
    }

    private String buildSupportMessage(ComplaintEntity c) {
        String username = c.getTelegramUsername() != null ? "@" + c.getTelegramUsername() : "—";
        String name = c.getTelegramName() != null ? c.getTelegramName() : "—";
        String contact = c.getContact() != null ? c.getContact() : "не указан";
        return """
                🆘 Новая жалоба (BatEnergy) #%d

                Пользователь: %s %s
                Telegram id: %s
                Контакт для связи: %s

                Сообщение:
                %s""".formatted(
                c.getId(),
                name,
                username,
                c.getTelegramUserId() != null ? String.valueOf(c.getTelegramUserId()) : "—",
                contact,
                c.getMessage());
    }
}
