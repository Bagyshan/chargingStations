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
 * Offline-детект станций. Online/offline ведётся по событиям {@code station.connectivity}
 * (CONNECTED/DISCONNECTED/HEARTBEAT), форвардимым из station-steve. Дополнительно периодический
 * свип гасит станции, от которых давно не было сигнала (страховка от пропущенного DISCONNECT,
 * напр. если station-controll лежал в момент отключения).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationConnectivityService {

    private final ChargeBoxRepository chargeBoxRepository;

    /** Считаем станцию offline, если сигнала не было дольше этого окна (по умолчанию 5 мин). */
    @Value("${station.offline-threshold-seconds:300}")
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
