package charg.ing.stations.service;

import charg.ing.stations.repository.ChargeBoxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

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

    /**
     * Окно тишины, после которого свип гасит online. Должно превышать интервал OCPP-heartbeat
     * станции (дефолт SteVe — 14400 c). По умолчанию 6 часов; уменьшайте только если у станций
     * частый heartbeat. Чистые отключения ловятся событием DISCONNECTED, а не этим окном.
     */
    @Value("${station.offline-threshold-seconds:21600}")
    private long offlineThresholdSeconds;

    @Transactional
    public void recordConnectivity(String chargeBoxId, String eventType, Instant timestamp) {
        boolean online = !"DISCONNECTED".equalsIgnoreCase(eventType);
        Instant seenAt = timestamp != null ? timestamp : Instant.now();
        int updated = chargeBoxRepository.updateConnectivity(chargeBoxId, online, seenAt);
        if (updated == 0) {
            log.debug("Connectivity event for unknown chargeBox {} ({})", chargeBoxId, eventType);
        } else {
            log.info("Connectivity: {} -> {} ({})", chargeBoxId, online ? "ONLINE" : "OFFLINE", eventType);
        }
    }

    /**
     * Отмечает станцию живой по любому входящему OCPP-сообщению (StatusNotification, MeterValue):
     * ставит online и обновляет {@code lastSeenAt}. Это делает признак online устойчивым к редкому
     * OCPP-heartbeat — пока станция шлёт данные, она online и не гасится свипом.
     */
    @Transactional
    public void markSeen(String chargeBoxId, Instant timestamp) {
        if (chargeBoxId == null) {
            return;
        }
        Instant seenAt = timestamp != null ? timestamp : Instant.now();
        int updated = chargeBoxRepository.updateConnectivity(chargeBoxId, true, seenAt);
        if (updated > 0) {
            log.debug("Liveness: {} seen at {}", chargeBoxId, seenAt);
        }
    }

    /** Свип: гасит online у станций, от которых не было сигнала дольше порога. */
    @Scheduled(fixedDelayString = "${station.offline-sweep-ms:60000}")
    @Transactional
    public void sweepOffline() {
        Instant threshold = Instant.now().minus(Duration.ofSeconds(offlineThresholdSeconds));
        int marked = chargeBoxRepository.markStaleOffline(threshold);
        if (marked > 0) {
            log.warn("Offline sweep: marked {} station(s) offline (no signal since {})", marked, threshold);
        }
    }
}
