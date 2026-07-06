package charg.ing.stations.consumer;

import charg.ing.stations.service.PushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Push-уведомления по событиям платформы (моменты отправки согласованы):
 *
 * <ul>
 *   <li>{@code charging.user.status} — терминальный статус зарядки:
 *       COMPLETED → «Зарядка завершена» (кВт·ч, сом); Faulted → «Сбой зарядки»;
 *       CANCELLED/REJECTED → «Зарядка прервана». Прогресс (ACTIVE) не пушим —
 *       его пользователь видит в приложении по WebSocket.</li>
 *   <li>{@code booking.state} — RESERVATION_PROGRESS с остатком ≤ 5 мин →
 *       «Бронь скоро истечёт» (один раз на бронь); COMPLETED / CANCELLED /
 *       PAYMENT_FAILED → терминальный пуш.</li>
 *   <li>{@code payment.topup.events} — каждое зачисленное пополнение →
 *       «Кошелёк пополнен» (сумма + новый баланс).</li>
 * </ul>
 *
 * <p>Дедупликация — bounded LRU по ключу события (терминальные статусы могут
 * приехать повторно при ребалансе/ретраях). Группа консюмера отдельная, offset
 * = latest: при первом деплое исторические события не превращаются в шквал пушей.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushEventConsumer {

    private static final int BOOKING_EXPIRING_THRESHOLD_MIN = 5;

    private final PushService pushService;
    private final ObjectMapper objectMapper;

    /** LRU последних обработанных ключей событий (дедуп повторных доставок). */
    private final Set<String> seen = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 2000;
                }
            }));

    // ── Зарядка ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = "charging.user.status",
            groupId = "notification-push",
            containerFactory = "kafkaListenerContainerFactory",
            properties = {"auto.offset.reset=latest"})
    public void onChargingStatus(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, String> record : records) {
            try {
                JsonNode j = objectMapper.readTree(record.value());
                String status = text(j, "status");
                String userId = text(j, "userId");
                if (userId == null || status == null || "ACTIVE".equalsIgnoreCase(status)) {
                    continue; // прогресс зарядки — только в приложении (WebSocket)
                }
                String txId = text(j, "transactionId");
                if (!markSeen("chg:" + txId + ":" + status)) continue;

                String station = text(j, "chargeBoxId");
                switch (status.toUpperCase()) {
                    case "COMPLETED" -> pushService.sendToUser(userId,
                            "Зарядка завершена",
                            chargingSummary(j, station),
                            Map.of("type", "charging", "status", status,
                                    "stationId", nvl(station), "transactionId", nvl(txId)));
                    case "FAULTED" -> pushService.sendToUser(userId,
                            "Сбой зарядки",
                            "Станция " + nvl(station) + " сообщила об ошибке. Проверьте автомобиль.",
                            Map.of("type", "charging", "status", status,
                                    "stationId", nvl(station), "transactionId", nvl(txId)));
                    default -> pushService.sendToUser(userId, // CANCELLED, REJECTED и пр.
                            "Зарядка прервана",
                            chargingSummary(j, station),
                            Map.of("type", "charging", "status", status,
                                    "stationId", nvl(station), "transactionId", nvl(txId)));
                }
            } catch (Exception e) {
                log.error("Failed to process charging status record: {}", e.getMessage());
            }
        }
        ack.acknowledge();
    }

    private String chargingSummary(JsonNode j, String station) {
        StringBuilder sb = new StringBuilder("Станция ").append(nvl(station));
        BigDecimal energy = decimal(j, "energyKwh");
        BigDecimal cost = decimal(j, "currentCost");
        if (energy != null) {
            sb.append(" · ").append(energy.setScale(1, RoundingMode.HALF_UP)).append(" кВт·ч");
        }
        if (cost != null) {
            sb.append(" · ").append(cost.setScale(0, RoundingMode.HALF_UP)).append(" сом");
        }
        return sb.toString();
    }

    // ── Бронь ────────────────────────────────────────────────────────────────

    @KafkaListener(topics = "booking.state",
            groupId = "notification-push",
            containerFactory = "kafkaListenerContainerFactory",
            properties = {"auto.offset.reset=latest"})
    public void onBookingEvent(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, String> record : records) {
            try {
                JsonNode j = objectMapper.readTree(record.value());
                String eventType = text(j, "eventType");
                String userId = text(j, "userId");
                String reservationId = text(j, "reservationId");
                if (eventType == null || userId == null) continue;
                JsonNode data = j.path("data");
                String station = text(data, "stationId");
                Map<String, String> payload = Map.of("type", "booking",
                        "eventType", eventType, "stationId", nvl(station),
                        "reservationId", nvl(reservationId));

                switch (eventType) {
                    case "RESERVATION_PROGRESS" -> {
                        int remaining = data.path("remainingBookingMinutes").asInt(Integer.MAX_VALUE);
                        if (remaining <= BOOKING_EXPIRING_THRESHOLD_MIN
                                && markSeen("bkexp:" + reservationId)) {
                            pushService.sendToUser(userId,
                                    "Бронь скоро истечёт",
                                    "Осталось ~" + Math.max(remaining, 0) + " мин на станции "
                                            + nvl(station) + ". Начните зарядку или продлите бронь.",
                                    payload);
                        }
                    }
                    case "RESERVATION_COMPLETED" -> {
                        if (markSeen("bk:" + reservationId + ":" + eventType)) {
                            pushService.sendToUser(userId, "Бронь завершена",
                                    bookingSummary(data, station), payload);
                        }
                    }
                    case "RESERVATION_CANCELLED" -> {
                        if (markSeen("bk:" + reservationId + ":" + eventType)) {
                            pushService.sendToUser(userId, "Бронь отменена",
                                    bookingSummary(data, station), payload);
                        }
                    }
                    case "RESERVATION_PAYMENT_FAILED" -> {
                        if (markSeen("bk:" + reservationId + ":" + eventType)) {
                            pushService.sendToUser(userId, "Бронь: оплата не прошла",
                                    "Недостаточно средств — бронь на станции " + nvl(station)
                                            + " остановлена. Пополните кошелёк.", payload);
                        }
                    }
                    default -> { /* CREATED/STARTED/BALANCE_UPDATED — только в приложении */ }
                }
            } catch (Exception e) {
                log.error("Failed to process booking record: {}", e.getMessage());
            }
        }
        ack.acknowledge();
    }

    private String bookingSummary(JsonNode data, String station) {
        StringBuilder sb = new StringBuilder("Станция ").append(nvl(station));
        int minutes = data.path("minutesElapsed").asInt(-1);
        BigDecimal cost = decimal(data, "currentCost");
        if (minutes >= 0) sb.append(" · ").append(minutes).append(" мин");
        if (cost != null) {
            sb.append(" · ").append(cost.setScale(0, RoundingMode.HALF_UP)).append(" сом");
        }
        return sb.toString();
    }

    // ── Кошелёк ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = "payment.topup.events",
            groupId = "notification-push",
            containerFactory = "kafkaListenerContainerFactory",
            properties = {"auto.offset.reset=latest"})
    public void onTopUp(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, String> record : records) {
            try {
                JsonNode j = objectMapper.readTree(record.value());
                String userId = text(j, "userId");
                BigDecimal amount = decimal(j, "amount");
                BigDecimal balance = decimal(j, "newBalance");
                if (userId == null || amount == null) continue;
                if (!markSeen("topup:" + userId + ":" + text(j, "timestamp") + ":" + amount)) continue;

                String body = "Зачислено " + amount.setScale(0, RoundingMode.HALF_UP) + " сом"
                        + (balance != null
                                ? ". Баланс: " + balance.setScale(0, RoundingMode.HALF_UP) + " сом"
                                : "");
                pushService.sendToUser(userId, "Кошелёк пополнен", body,
                        Map.of("type", "topup"));
            } catch (Exception e) {
                log.error("Failed to process top-up record: {}", e.getMessage());
            }
        }
        ack.acknowledge();
    }

    // ── Утилиты ──────────────────────────────────────────────────────────────

    /** true — событие новое (добавлено); false — уже обрабатывали. */
    private boolean markSeen(String key) {
        return seen.add(key);
    }

    private static String text(JsonNode j, String field) {
        JsonNode n = j.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private static BigDecimal decimal(JsonNode j, String field) {
        JsonNode n = j.path(field);
        return n.isNumber() ? n.decimalValue() : null;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
