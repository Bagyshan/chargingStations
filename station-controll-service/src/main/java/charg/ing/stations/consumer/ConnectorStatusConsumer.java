package charg.ing.stations.consumer;

import charg.ing.stations.dto.event.ChargingStatusEvent;
import charg.ing.stations.dto.event.StationAlertEvent;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.entity.TransactionEntity;
import charg.ing.stations.enums.ConnectorStatus;
import charg.ing.stations.enums.TransactionStatus;
import charg.ing.stations.producer.ChargingStatusProducer;
import charg.ing.stations.producer.StationAlertProducer;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.repository.TransactionRepository;
import charg.ing.stations.service.ChargingStopService;
import charg.ing.stations.service.ConnectorService;
import charg.ing.stations.service.StationConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Реакция на OCPP StatusNotification, форвардимый из station-steve в топик {@code station.status}.
 *
 * <ol>
 *   <li>держит operational-статус коннектора актуальным ({@code connector.status});</li>
 *   <li>при ПЕРЕХОДЕ в {@code Faulted}/{@code Unavailable}: алертит операторов (владелец +
 *       ADMIN/SPECIALIST через топик {@code station.alerts} → user-service) и, если идёт активная
 *       зарядка — graceful-stop + пуш {@code status=Faulted} инициатору в {@code charging.user.status}.</li>
 * </ol>
 *
 * <p>Алерт/реакция срабатывают только на ПЕРЕХОДЕ (был исправен → стал неисправен), чтобы повторные
 * StatusNotification не спамили письмами.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorStatusConsumer {

    private final ConnectorService connectorService;
    private final ConnectorRepository connectorRepository;
    private final ChargeBoxRepository chargeBoxRepository;
    private final TransactionRepository transactionRepository;
    private final ChargingStopService chargingStopService;
    private final ChargingStatusProducer chargingStatusProducer;
    private final StationAlertProducer stationAlertProducer;
    private final StationConnectivityService connectivityService;

    @KafkaListener(topics = "station.status", groupId = "station-controller-service-group-status")
    public void onConnectorStatus(Map<String, Object> message, Acknowledgment ack) {
        try {
            Object chargeBoxIdObj = message.get("chargeBoxId");
            Object connectorIdObj = message.get("connectorId");
            Object statusObj = message.get("status");
            if (chargeBoxIdObj == null || connectorIdObj == null || statusObj == null) {
                return;
            }
            String chargeBoxId = chargeBoxIdObj.toString();
            int connectorId = Integer.parseInt(connectorIdObj.toString());
            String status = statusObj.toString();
            String errorCode = message.get("errorCode") != null ? message.get("errorCode").toString() : null;

            log.info("Connector status: {}:{} -> {}", chargeBoxId, connectorId, status);

            // Любой StatusNotification — признак, что станция на связи (держит online актуальным
            // даже при редком OCPP-heartbeat).
            connectivityService.markSeen(chargeBoxId, Instant.now());

            // Старый статус (для детекта перехода) до обновления.
            String oldStatus = connectorRepository.findByChargeBoxIdAndConnectorId(chargeBoxId, connectorId)
                    .map(ConnectorEntity::getStatus)
                    .orElse(null);

            // 1. Держим operational-статус коннектора живым (короткая транзакция).
            connectorService.updateOperationalStatus(chargeBoxId, connectorId, status);

            // 2. Реагируем только на ПЕРЕХОД в неисправное состояние.
            if (isOutOfOrder(status) && !isOutOfOrder(oldStatus)) {
                alertOperators(chargeBoxId, connectorId, status, errorCode);
                reactToFault(chargeBoxId, connectorId, status);
            }
        } catch (Exception e) {
            log.error("Failed to process connector status event: {}", e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }

    /** Публикует алерт о неисправности в {@code station.alerts} (получателей резолвит user-service). */
    private void alertOperators(String chargeBoxId, int connectorId, String status, String errorCode) {
        ChargeBoxEntity box = chargeBoxRepository.findByChargeBoxId(chargeBoxId);
        String ownerId = box != null ? box.getOwnerId() : null;
        stationAlertProducer.publish(StationAlertEvent.builder()
                .chargeBoxId(chargeBoxId)
                .connectorId(connectorId)
                .ownerId(ownerId)
                .status(status)
                .errorCode(errorCode)
                .timestamp(Instant.now())
                .build());
    }

    private void reactToFault(String chargeBoxId, int connectorId, String status) {
        Optional<TransactionEntity> active = transactionRepository
                .findFirstByChargeBoxIdAndConnectorIdAndStatusOrderByIdDesc(
                        chargeBoxId, connectorId, TransactionStatus.ACTIVE);
        if (active.isEmpty()) {
            return; // нет активной сессии — нечего останавливать
        }
        TransactionEntity tx = active.get();
        log.warn("Connector {}:{} went {} during active tx {} — notifying user {} and stopping",
                chargeBoxId, connectorId, status, tx.getTransactionId(), tx.getUserId());

        // Сначала уведомляем инициатора (remote-STOP блокирующий, может занять секунды).
        notifyInitiator(tx, status);
        chargingStopService.stopActiveTransaction(tx, "connector " + status);
    }

    private void notifyInitiator(TransactionEntity tx, String status) {
        if (tx.getUserId() == null) {
            return;
        }
        chargingStatusProducer.publish(ChargingStatusEvent.builder()
                .userId(tx.getUserId())
                .chargeBoxId(tx.getChargeBoxId())
                .connectorId(tx.getConnectorId())
                .transactionId(tx.getTransactionId())
                .kwCost(tx.getPricePerKwh())
                .maxKwQuantity(tx.getMaxKwQuantity())
                .startedAt(tx.getStartTimestamp())
                .status(status)
                .timestamp(Instant.now())
                .build());
    }

    /** Сломанные/выведенные станцией состояния OCPP, при которых надо прервать активную зарядку. */
    private boolean isOutOfOrder(String status) {
        return ConnectorStatus.FAULTED.getValue().equalsIgnoreCase(status)
                || ConnectorStatus.UNAVAILABLE.getValue().equalsIgnoreCase(status);
    }
}
