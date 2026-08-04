package charg.ing.stations.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки Telegram-бота поддержки.
 * Значения приходят из application.yaml (telegram.bot.*), которые в проде
 * переопределяются переменными окружения TELEGRAM_BOT_TOKEN / TELEGRAM_SUPPORT_CHAT_ID.
 */
@Data
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {

    /** Токен бота из @BotFather. Без него long polling не запускается. */
    private String token;

    /**
     * Куда форвардить жалобы — id личного чата, группы или канала поддержки.
     * Для групп это отрицательное число (например, -1001234567890). Может быть
     * пустым: тогда жалоба только сохраняется в БД (форвард пропускается с WARN).
     */
    private String supportChatId;

    /** Таймаут long polling getUpdates, сек. Telegram держит соединение до этого времени. */
    private int pollTimeoutSeconds = 30;

    /** Базовый URL Telegram Bot API (переопределяется только для тестов/прокси). */
    private String apiBase = "https://api.telegram.org";

    /** Бот включён (есть токен) — иначе поллер не стартует. */
    public boolean isEnabled() {
        return token != null && !token.isBlank();
    }

    /** Есть ли настроенный чат поддержки для форварда. */
    public boolean hasSupportChat() {
        return supportChatId != null && !supportChatId.isBlank();
    }
}
