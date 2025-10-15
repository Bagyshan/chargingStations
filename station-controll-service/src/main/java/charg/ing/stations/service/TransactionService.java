package charg.ing.stations.service;


import charg.ing.stations.dto.StartTransactionCreateEvent;
import charg.ing.stations.dto.StopTransactionUpdateEvent;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.entity.TransactionEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.repository.TransactionRepository;
import charg.ing.stations.service.interfaces.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import charg.ing.stations.enums.ConnectorStatus;

import java.time.Instant;
import java.util.Map;

@Service
@Transactional
public class TransactionService implements EventService {

    private final TransactionRepository repository;
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;
    private final StationStateService stationStateService;
    private final ChargeBoxRepository chargeBoxRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public TransactionService(
            TransactionRepository transactionRepository,
            ConnectorRepository connectorRepository, ConnectorService connectorService,
            StationStateService stationStateService, ChargeBoxRepository chargeBoxRepository,
            ObjectMapper objectMapper) {
        this.repository = transactionRepository;
        this.connectorRepository = connectorRepository;
        this.connectorService = connectorService;
        this.stationStateService = stationStateService;
        this.chargeBoxRepository = chargeBoxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public void saveStartTransactionAndAck(StartTransactionCreateEvent req, Acknowledgment ack) {
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
            entity.setStartValue(Integer.valueOf(req.getStartValue()));
            entity.setStatus(req.getStatus());
            entity.setCreatedAt(Instant.now());

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


            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ack.acknowledge();
                }
            });

        } catch (Exception e) {
            ack.acknowledge();
            throw e;
        }
    }


    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public void updateStopTransactionAndAck(StopTransactionUpdateEvent req, Acknowledgment ack) {
        try {
            TransactionEntity entity = repository.findByTransactionId(req.getTransactionId())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found with id: " + req.getTransactionId()));

            if (req.getStopTimestamp() != null) {
                entity.setStopTimestamp(req.getStopTimestamp());
            } else {
                entity.setStopTimestamp(Instant.now());
            }
            entity.setStopValue(Integer.valueOf(req.getStopValue()));
            entity.setTransactionValue(Integer.parseInt(req.getStopValue()) - entity.getStartValue());
            entity.setStatus(req.getStatus());
            entity.setUpdatedAt(Instant.now());

            repository.save(entity);
            repository.flush();

            connectorRepository.updateStatusByConnectorIdAndChargeBoxId(
                    req.getConnectorId(),
                    req.getChargeBoxId(),
                    ConnectorStatus.AVAILABLE.getValue()
            );

            ConnectorEntity connector = connectorRepository.findConnectorForUpdate(req.getConnectorId(), req.getChargeBoxId());
            ChargeBoxEntity chargeBox = chargeBoxRepository.findChargeBoxForUpdate(req.getChargeBoxId());

            connector.setVersion(connector.getVersion() + 1);
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


            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ack.acknowledge();
                }
            });
        } catch (Exception e) {
            ack.acknowledge();
            throw e;
        }
    }


    @Override
    public void processEvent(Map<String, Object> message, Acknowledgment ack, String topic) {
        String actionType = (String) message.get("actionType");

        if ("START_TRANSACTION".equals(actionType)) {
            StartTransactionCreateEvent conn = objectMapper.convertValue(message, StartTransactionCreateEvent.class);
            saveStartTransactionAndAck(conn, ack);
        } else if ("STOP_TRANSACTION".equals(actionType)) {
            StopTransactionUpdateEvent conn = objectMapper.convertValue(message, StopTransactionUpdateEvent.class);
            updateStopTransactionAndAck(conn, ack);
        } else {
            ack.acknowledge(); // Подтверждаем неподдерживаемые actionType
        }
    }
}
