package charg.ing.stations.service;


import charg.ing.stations.dto.StartTransactionCreateEvent;
import charg.ing.stations.dto.StopTransactionUpdateEvent;
import charg.ing.stations.dto.TransactionHistoryDTO;
import charg.ing.stations.dto.TransactionResponseDTO;
import charg.ing.stations.dto.event.ChargingStatusEvent;
import charg.ing.stations.dto.event.TransactionEventMessage;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.entity.TransactionEntity;
import charg.ing.stations.enums.ConnectorStatus;
import charg.ing.stations.enums.TransactionStatus;
import charg.ing.stations.producer.ChargingStatusProducer;
import charg.ing.stations.producer.TransactionEventProducer;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.repository.TransactionRepository;
import charg.ing.stations.service.interfaces.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class TransactionService implements EventService {

    private final TransactionRepository repository;
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;
    private final StationStateService stationStateService;
    private final ChargeBoxRepository chargeBoxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionEventProducer transactionEventProducer;
    private final ChargingBalanceClient chargingBalanceClient;
    private final ChargingStatusProducer chargingStatusProducer;

    private static final Duration BALANCE_TIMEOUT = Duration.ofSeconds(7);

    @Autowired
    public TransactionService(
            TransactionRepository transactionRepository,
            ConnectorRepository connectorRepository, ConnectorService connectorService,
            StationStateService stationStateService, ChargeBoxRepository chargeBoxRepository,
            ObjectMapper objectMapper, TransactionEventProducer transactionEventProducer,
            ChargingBalanceClient chargingBalanceClient, ChargingStatusProducer chargingStatusProducer) {
        this.repository = transactionRepository;
        this.connectorRepository = connectorRepository;
        this.connectorService = connectorService;
        this.stationStateService = stationStateService;
        this.chargeBoxRepository = chargeBoxRepository;
        this.objectMapper = objectMapper;
        this.transactionEventProducer = transactionEventProducer;
        this.chargingBalanceClient = chargingBalanceClient;
        this.chargingStatusProducer = chargingStatusProducer;
    }

    /** Resolved charging budget: price per kWh and the max kWh the wallet can fund (null = unlimited/free). */
    public record ChargingLimit(BigDecimal pricePerKwh, BigDecimal maxKwQuantity) {}

    /**
     * История зарядок пользователя (все статусы), новые — первыми.
     * Плоский DTO — ленивые связи сущности не трогаем.
     */
    @Transactional(readOnly = true)
    public List<TransactionHistoryDTO> getUserTransactions(String userId) {
        return repository.findByUserIdOrderByIdDesc(userId).stream()
                .map(this::toHistory)
                .toList();
    }

    private TransactionHistoryDTO toHistory(TransactionEntity t) {
        return TransactionHistoryDTO.builder()
                .transactionId(t.getTransactionId())
                .chargeBoxId(t.getChargeBoxId())
                .connectorId(t.getConnectorId())
                .status(t.getStatus())
                .reason(t.getReason())
                .startTimestamp(t.getStartTimestamp())
                .stopTimestamp(t.getStopTimestamp())
                .startValue(t.getStartValue())
                .stopValue(t.getStopValue())
                .transactionValue(t.getTransactionValue())
                .totalSum(t.getTotalSum())
                .pricePerKwh(t.getPricePerKwh())
                .maxKwQuantity(t.getMaxKwQuantity())
                .createdAt(t.getCreatedAt())
                .build();
    }

    /**
     * Computes the kWh budget for a session from the charge box tariff ({@code kw_cost}) and the user's
     * wallet balance (fetched from payment-service). When the tariff is unset/zero the session is free
     * and {@code maxKwQuantity} is {@code null} (no budget cap).
     */
    public ChargingLimit computeChargingLimit(String userId, String chargeBoxId) {
        ChargeBoxEntity chargeBox = chargeBoxRepository.findByChargeBoxId(chargeBoxId);
        BigDecimal price = chargeBox != null ? chargeBox.getKwCost() : null;
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return new ChargingLimit(price, null); // free charging — no cap
        }
        BigDecimal balance = chargingBalanceClient.getBalance(UUID.fromString(userId), BALANCE_TIMEOUT);
        BigDecimal maxKw = balance.divide(price, 3, RoundingMode.DOWN);
        return new ChargingLimit(price, maxKw);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public void saveStartTransactionAndAck(TransactionResponseDTO req, Acknowledgment ack) {
        try {
            TransactionEntity entity = new TransactionEntity();
            entity.setTransactionId(req.getTransactionId());
            entity.setChargeBoxId(req.getChargeBoxId());
            entity.setConnectorId(req.getConnectorId());
            if (req.getStartTimestamp() != null) {
                entity.setStartTimestamp(req.getStartTimestamp());
            } else {
                entity.setStartTimestamp(Instant.now());
            }
            entity.setStartValue(req.getStartValue() != null ? Integer.valueOf(req.getStartValue()) : 0);
            entity.setStatus(req.getStatus());
            entity.setUserId(req.getUserId());
            entity.setCreatedAt(Instant.now());

            // Charging budget: use the pre-checked values from the controller, otherwise resolve here
            // (e.g. a charger-initiated start that did not pass through the REST pre-check).
            BigDecimal pricePerKwh = req.getPricePerKwh();
            BigDecimal maxKwQuantity = req.getMaxKwQuantity();
            if (maxKwQuantity == null && pricePerKwh == null) {
                ChargingLimit limit = computeChargingLimit(req.getUserId(), req.getChargeBoxId());
                pricePerKwh = limit.pricePerKwh();
                maxKwQuantity = limit.maxKwQuantity();
                if (maxKwQuantity != null && maxKwQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Charger-initiated start with insufficient balance for user {} on {} — " +
                            "auto-stop will trigger on first meter value", req.getUserId(), req.getChargeBoxId());
                }
            }
            entity.setPricePerKwh(pricePerKwh);
            entity.setMaxKwQuantity(maxKwQuantity);

            repository.save(entity);
            repository.flush();

            connectorRepository.updateStatusByConnectorIdAndChargeBoxId(
                    req.getConnectorId(),
                    req.getChargeBoxId(),
                    ConnectorStatus.CHARGING.getValue()
            );

            ConnectorEntity connector = connectorRepository.findConnectorForUpdate(req.getConnectorId(), req.getChargeBoxId());
            ChargeBoxEntity chargeBox = chargeBoxRepository.findChargeBoxForUpdate(req.getChargeBoxId());

            connector.setVersion(connector.getVersion() + 1);
            connector.setChargingUserId(req.getUserId());
            // Старт зарядки «поглощает» бронь этого коннектора: снимаем удержание, чтобы
            // событие STOP_RESERVATION (при завершении брони) не считало коннектор занятым.
            connector.setBookingUserId(null);
            chargeBox.setVersion(chargeBox.getVersion() + 1);

            connectorRepository.save(connector);
            chargeBoxRepository.save(chargeBox);

            stationStateService.publishStationState(
                    req.getChargeBoxId(),
                    req.getConnectorId(),
                    ConnectorStatus.CHARGING.getValue(),
                    connector.getVersion(),
                    chargeBox.getVersion()
            );

            TransactionEventMessage eventMessage = TransactionEventMessage.builder()
                    .id(entity.getId())
                    .transactionId(entity.getTransactionId())
                    .chargeBoxId(entity.getChargeBoxId())
                    .connectorId(entity.getConnectorId())
                    .startTimestamp(entity.getStartTimestamp())
                    .startValue(entity.getStartValue())
                    .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                    .userId(entity.getUserId())
                    .createdAt(entity.getCreatedAt())
                    .build();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ack.acknowledge();
                    transactionEventProducer.publish(eventMessage);
                }
            });

        } catch (Exception e) {
            ack.acknowledge();
            throw e;
        }
    }


    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public void updateStopTransactionAndAck(TransactionResponseDTO req, Acknowledgment ack) {
        try {
            TransactionEntity entity = repository
                    .findFirstByChargeBoxIdAndConnectorIdAndStatusOrderByIdDesc(
                            req.getChargeBoxId(), req.getConnectorId(), TransactionStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Active transaction not found for chargeBox=" + req.getChargeBoxId()
                            + " connector=" + req.getConnectorId()));

            if (!entity.getUserId().equals(req.getUserId())) {
               throw new SecurityException("User not authorized to stop this transaction");
            }
            if (req.getStopTimestamp() != null) {
                entity.setStopTimestamp(req.getStopTimestamp());
            } else {
                entity.setStopTimestamp(Instant.now());
            }
            int stopVal = req.getStopValue() != null ? Integer.parseInt(req.getStopValue()) : 0;
            entity.setStopValue(stopVal);
            int consumedWh = stopVal - (entity.getStartValue() != null ? entity.getStartValue() : 0);
            if (consumedWh < 0) consumedWh = 0;
            entity.setTransactionValue(consumedWh);

            // Settle: cost = consumed kWh × price per kWh (payment-service debits this on the stop event).
            BigDecimal totalSum = BigDecimal.ZERO;
            if (entity.getPricePerKwh() != null) {
                totalSum = BigDecimal.valueOf(consumedWh)
                        .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP)
                        .multiply(entity.getPricePerKwh())
                        .setScale(2, RoundingMode.HALF_UP);
            }
            entity.setTotalSum(totalSum);
            entity.setStatus(req.getStatus());
            entity.setUpdatedAt(Instant.now());
            entity.setUserId(req.getUserId());

            repository.save(entity);
            repository.flush();

            log.error("Connector saving: {}, {}", req.getConnectorId(), req.getChargeBoxId());
            connectorRepository.updateStatusByConnectorIdAndChargeBoxId(
                    req.getConnectorId(),
                    req.getChargeBoxId(),
                    ConnectorStatus.AVAILABLE.getValue()
            );
            log.error("Connector saving: {}, {}", req.getConnectorId(), req.getChargeBoxId());

            ConnectorEntity connector = connectorRepository.findConnectorForUpdate(req.getConnectorId(), req.getChargeBoxId());
            ChargeBoxEntity chargeBox = chargeBoxRepository.findChargeBoxForUpdate(req.getChargeBoxId());


            connector.setStatus(ConnectorStatus.AVAILABLE.getValue());
            connector.setVersion(connector.getVersion() + 1);
            connector.setChargingUserId(null);
            // Фиксируем конец зарядки — с этого момента коннектор нельзя бронировать 10 минут.
            connector.setLastChargingEndedAt(Instant.now());
            chargeBox.setVersion(chargeBox.getVersion() + 1);

            connectorRepository.save(connector);
            chargeBoxRepository.save(chargeBox);

            stationStateService.publishStationState(
                    req.getChargeBoxId(),
                    req.getConnectorId(),
                    ConnectorStatus.AVAILABLE.getValue(),
                    connector.getVersion(),
                    chargeBox.getVersion()
            );

            TransactionEventMessage eventMessage = TransactionEventMessage.builder()
                    .id(entity.getId())
                    .transactionId(entity.getTransactionId())
                    .chargeBoxId(entity.getChargeBoxId())
                    .connectorId(entity.getConnectorId())
                    .startTimestamp(entity.getStartTimestamp())
                    .startValue(entity.getStartValue())
                    .stopTimestamp(entity.getStopTimestamp())
                    .stopValue(entity.getStopValue())
                    .transactionValue(entity.getTransactionValue())
                    .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                    .reason(entity.getReason())
                    .userId(entity.getUserId())
                    .totalSum(entity.getTotalSum())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();

            // Терминальный статус зарядки инициатору по WebSocket (charging.user.status):
            // без него мобильный экран активной зарядки не закроется при авто-стопе (баланс исчерпан,
            // поломка коннектора) — meter-значения после STOP больше не идут, а последний статус был ACTIVE.
            ChargingStatusEvent terminalStatus = buildTerminalStatus(entity);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ack.acknowledge();
                    transactionEventProducer.publish(eventMessage);
                    chargingStatusProducer.publish(terminalStatus);
                }
            });
        } catch (Exception e) {
            ack.acknowledge();
            throw e;
        }
    }

    /** Итоговый статус завершённой транзакции для per-user WebSocket-канала зарядки. */
    private ChargingStatusEvent buildTerminalStatus(TransactionEntity entity) {
        BigDecimal energyKwh = null;
        if (entity.getTransactionValue() != null) {
            energyKwh = BigDecimal.valueOf(entity.getTransactionValue())
                    .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
        }
        return ChargingStatusEvent.builder()
                .userId(entity.getUserId())
                .chargeBoxId(entity.getChargeBoxId())
                .connectorId(entity.getConnectorId())
                .transactionId(entity.getTransactionId())
                .energyKwh(energyKwh)
                .currentCost(entity.getTotalSum())
                .kwCost(entity.getPricePerKwh())
                .maxKwQuantity(entity.getMaxKwQuantity())
                .startedAt(entity.getStartTimestamp())
                .soc(null)
                .status(entity.getStatus() != null ? entity.getStatus().name() : TransactionStatus.COMPLETED.name())
                .timestamp(Instant.now())
                .build();
    }


    /**
     * Recomputes {@code max_kw_quantity} for the user's active transaction after a wallet top-up.
     * A top-up only grows the balance, so the budget can only extend; a non-increasing result is a no-op.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public void recomputeMaxKw(String userId, BigDecimal newBalance) {
        if (userId == null || newBalance == null) {
            return;
        }
        repository.findFirstByUserIdAndStatusOrderByIdDesc(userId, TransactionStatus.ACTIVE)
                .ifPresent(tx -> {
                    BigDecimal price = tx.getPricePerKwh();
                    if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                        return; // free charging — no budget cap
                    }
                    BigDecimal newMax = newBalance.divide(price, 3, RoundingMode.DOWN);
                    BigDecimal current = tx.getMaxKwQuantity() != null ? tx.getMaxKwQuantity() : BigDecimal.ZERO;
                    if (newMax.compareTo(current) > 0) {
                        tx.setMaxKwQuantity(newMax);
                        tx.setUpdatedAt(Instant.now());
                        repository.save(tx);
                        log.info("Extended charging budget for user {}: maxKw {} -> {} (new balance {})",
                                userId, current, newMax, newBalance);
                    }
                });
    }

    @Override
    public void processEvent(Map<String, Object> message, Acknowledgment ack, String topic) {
        String actionType = (String) message.get("actionType");

        if ("START_TRANSACTION".equals(actionType)) {
            TransactionResponseDTO conn = objectMapper.convertValue(message, TransactionResponseDTO.class);
            saveStartTransactionAndAck(conn, ack);
        } else if ("STOP_TRANSACTION".equals(actionType)) {
            TransactionResponseDTO conn = objectMapper.convertValue(message, TransactionResponseDTO.class);
            updateStopTransactionAndAck(conn, ack);
        } else {
            ack.acknowledge(); // Подтверждаем неподдерживаемые actionType
        }
    }
}
