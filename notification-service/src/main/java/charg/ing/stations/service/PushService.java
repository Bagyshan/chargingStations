package charg.ing.stations.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Отправка FCM push-уведомлений на устройства пользователя.
 *
 * <p>Инициализируется из service-account JSON ({@code fcm.service-account-file}).
 * Если файл не задан/не найден — сервис работает в режиме no-op (лог WARN один раз),
 * ничего не падает: e-mail рассылка и остальной функционал не зависят от FCM.
 *
 * <p>Токены устройств живут в user-service (владелец пользовательских данных);
 * получаем их по внутреннему API. Протухшие токены (FCM UNREGISTERED) удаляем там же.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final DeviceTokenClient deviceTokenClient;

    @Value("${fcm.service-account-file:}")
    private String serviceAccountFile;

    private FirebaseMessaging messaging; // null => пуши выключены

    @PostConstruct
    void init() {
        if (serviceAccountFile == null || serviceAccountFile.isBlank()
                || !Files.exists(Path.of(serviceAccountFile))) {
            log.warn("FCM disabled: service-account file not found ({}). " +
                    "Set FCM_SERVICE_ACCOUNT_FILE to enable push notifications.", serviceAccountFile);
            return;
        }
        try (FileInputStream in = new FileInputStream(serviceAccountFile)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            messaging = FirebaseMessaging.getInstance(app);
            log.info("FCM initialized from {}", serviceAccountFile);
        } catch (Exception e) {
            log.error("Failed to initialize FCM: {}", e.getMessage(), e);
        }
    }

    public boolean isEnabled() {
        return messaging != null;
    }

    /**
     * Пуш на все устройства пользователя (keycloakId = JWT sub — тот же id,
     * что в событиях charging/booking/payment).
     */
    public void sendToUser(String keycloakId, String title, String body, Map<String, String> data) {
        if (messaging == null) {
            log.debug("FCM disabled, skipping push '{}' for user {}", title, keycloakId);
            return;
        }
        List<String> tokens = deviceTokenClient.tokensForUser(keycloakId);
        if (tokens.isEmpty()) {
            log.debug("No device tokens for user {}, skipping push '{}'", keycloakId, title);
            return;
        }
        for (String token : tokens) {
            sendToToken(token, title, body, data);
        }
    }

    private void sendToToken(String token, String title, String body, Map<String, String> data) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build());
        if (data != null) {
            builder.putAllData(data);
        }
        try {
            String id = messaging.send(builder.build());
            log.info("Push sent: '{}' -> {} (messageId {})", title, mask(token), id);
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                log.info("Pruning stale device token {} ({})", mask(token), code);
                deviceTokenClient.deleteToken(token);
            } else {
                log.error("Push failed for token {}: {} ({})", mask(token), e.getMessage(), code);
            }
        }
    }

    private static String mask(String token) {
        return token.length() <= 12 ? "***" : token.substring(0, 12) + "…";
    }
}
