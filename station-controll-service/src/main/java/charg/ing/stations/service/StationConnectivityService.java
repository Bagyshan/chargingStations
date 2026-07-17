package charg.ing.stations.service;

import charg.ing.stations.audit.AuditEventPublisher;
import charg.ing.stations.config.StationTimeoutProperties;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline-детект станций. Признак online ведётся по двум источникам:
 * <ol>
 *   <li><b>Рёбра подключения</b> — события {@code station.connectivity}
 *       (CONNECTED/DISCONNECTED/HEARTBEAT), форвардимые из station-steve. Это основной и точный
 *       сигнал: CONNECTED ставит online на открытии WebSocket, DISCONNECTED — мгновенно гасит на
 *       закрытии.</li>
 *   <li><b>Активность станции</b> — {@link #markSeen} вызывается при любом входящем OCPP-сообщении
 *       (StatusNotification, MeterValue): раз станция шлёт данные — она на связи.</li>
 * </ol>
 *
 * <p>Свип ({@link #sweepOffline}) — лишь страховка от пропущенного DISCONNECT (напр. краш SteVe без
 * чистого закрытия сокета). Его порог должен быть БОЛЬШЕ интервала OCPP-heartbeat, иначе он будет
 * ложно гасить живые, но «молчаливые» станции: SteVe по умолчанию выдаёт heartbeat раз в 4 часа
 * (14400 c), поэтому порог по умолчанию — 6 часов. Чистые отключения и так ловятся DISCONNECT
 * мгновенно, так что большое окно свипа не вредит отзывчивости.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationConnectivityService {

    private final ChargeBoxRepository chargeBoxRepository;
    private final StationStateService stationStateService;
    private final AuditEventPublisher auditPublisher;
    /**
     * Окно тишины offline-свипа. Держим как refreshable-холдер (не @Value-поле), чтобы порог
     * можно было менять из Consul KV на лету — значение читается в {@link #sweepOffline()}
     * в момент прогона.
     */
    private final StationTimeoutProperties timeoutProps;

    @Transactional
    public void recordConnectivity(String chargeBoxId, String eventType, Instant timestamp) {
        boolean online = !"DISCONNECTED".equalsIgnoreCase(eventType);
        applyOnline(chargeBoxId, online, timestamp, eventType);
    }

    /**
     * Отмечает станцию живой по любому входящему OCPP-сообщению (StatusNotification, MeterValue):
     * ставит online и обновляет {@code lastSeenAt}. Это делает признак online устойчивым к редкому
     * OCPP-heartbeat — пока станция шлёт данные, она online и не гасится свипом.
     */
    @Transactional
    public void markSeen(String chargeBoxId, Instant timestamp) {
        applyOnline(chargeBoxId, true, timestamp, "ACTIVITY");
    }

    /**
     * Применяет признак online. На ПЕРЕХОДЕ online (изменилось значение) — бампает версию и
     * публикует снимок состояния, чтобы изменение само дошло до Redis-кэша (state-updater) без
     * ручного reload. Если online не изменился — лишь освежает {@code lastSeenAt} (дёшево, без
     * бампа версии и без снимка).
     */
    private void applyOnline(String chargeBoxId, boolean online, Instant timestamp, String reason) {
        if (chargeBoxId == null) {
            return;
        }
        Boolean current = chargeBoxRepository.findOnlineByChargeBoxId(chargeBoxId);
        if (current == null && !chargeBoxRepository.existsByChargeBoxId(chargeBoxId)) {
            log.debug("Connectivity event for unknown chargeBox {} ({})", chargeBoxId, reason);
            return;
        }
        Instant seenAt = timestamp != null ? timestamp : Instant.now();

        if (Boolean.valueOf(online).equals(current)) {
            // Состояние не изменилось — только метка последнего сигнала.
            chargeBoxRepository.touchLastSeen(chargeBoxId, seenAt);
            return;
        }

        chargeBoxRepository.updateConnectivityAndBumpVersion(chargeBoxId, online, seenAt);
        publishSnapshot(chargeBoxId, online ? "ONLINE" : "OFFLINE", reason);

        Map<String, Object> payload = new HashMap<>();
        payload.put("online", online);
        payload.put("reason", reason);
        auditPublisher.publishChargeBox(online ? "CONNECTED" : "DISCONNECTED", chargeBoxId, null,
                online ? "INFO" : "WARN",
                "Station " + chargeBoxId + (online ? " online" : " offline") + " (" + reason + ")", payload);
    }

    /** Свип: гасит online у станций, от которых не было сигнала дольше порога. */
    @Scheduled(fixedDelayString = "${station.offline-sweep-ms:60000}")
    @Transactional
    public void sweepOffline() {
        Instant threshold = Instant.now().minus(Duration.ofSeconds(timeoutProps.getOfflineThresholdSeconds()));
        List<String> stale = chargeBoxRepository.findStaleOnlineChargeBoxIds(threshold);
        for (String chargeBoxId : stale) {
            chargeBoxRepository.markOfflineAndBumpVersion(chargeBoxId);
            publishSnapshot(chargeBoxId, "OFFLINE", "sweep");
            Map<String, Object> payload = new HashMap<>();
            payload.put("online", false);
            payload.put("reason", "sweep");
            auditPublisher.publishChargeBox("DISCONNECTED", chargeBoxId, null, "WARN",
                    "Station " + chargeBoxId + " offline (sweep timeout)", payload);
        }
        if (!stale.isEmpty()) {
            log.warn("Offline sweep: marked {} station(s) offline (no signal since {})", stale.size(), threshold);
        }
    }

    /** Публикует снимок состояния станции с её текущей (уже забампленной) версией. */
    private void publishSnapshot(String chargeBoxId, String state, String reason) {
        ChargeBoxEntity fresh = chargeBoxRepository.findByChargeBoxId(chargeBoxId);
        if (fresh == null) {
            return;
        }
        long version = fresh.getVersion() != null ? fresh.getVersion() : 0L;
        stationStateService.publishStationSnapshot(chargeBoxId, version);
        log.info("Connectivity: {} -> {} ({}) snapshot v{}", chargeBoxId, state, reason, version);
    }
}
