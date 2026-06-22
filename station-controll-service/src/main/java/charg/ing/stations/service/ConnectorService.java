package charg.ing.stations.service;

import charg.ing.stations.dto.ConnectorCreateEvent;
import charg.ing.stations.dto.ConnectorPatchDTO;
import charg.ing.stations.dto.StationCreateEvent;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.entity.ConnectorTypeEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.repository.ConnectorTypeRepository;
import charg.ing.stations.service.interfaces.EventService;
import charg.ing.stations.service.util.ConnectorTransactionalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@Slf4j
@Service
public class ConnectorService implements EventService {

    private final ConnectorRepository repository;
    private final ConnectorTypeRepository connectorTypeRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConnectorService(ConnectorRepository repository, ConnectorTypeRepository connectorTypeRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.connectorTypeRepository = connectorTypeRepository;
        this.objectMapper = objectMapper;
    }

    public ConnectorEntity getConnector(int connectorId, String chargeBoxId) {
        return repository.findByConnectorIdAndChargeBoxId(connectorId, chargeBoxId);
    }

    /** Обновляет operational-статус коннектора по OCPP StatusNotification (топик {@code station.status}). */
    @Transactional
    public void updateOperationalStatus(String chargeBoxId, int connectorId, String status) {
        repository.updateStatusByConnectorIdAndChargeBoxId(connectorId, chargeBoxId, status);
    }

    @Transactional
    public ConnectorPatchDTO patchConnector(String chargeBoxId, int connectorId, ConnectorPatchDTO dto) {
        ConnectorEntity entity = repository.findByConnectorIdAndChargeBoxId(connectorId, chargeBoxId);
        if (entity == null) {
            throw new RuntimeException("Connector not found: chargeBoxId=" + chargeBoxId + ", connectorId=" + connectorId);
        }
        if (dto.getInfo() != null) entity.setInfo(dto.getInfo());
        if (dto.getVendorId() != null) entity.setVendorId(dto.getVendorId());
        if (dto.getConnectorTypeId() != null) {
            entity.setConnectorType(connectorTypeRepository.getReferenceById(dto.getConnectorTypeId()));
        }
        repository.save(entity);
        return dto;
    }

    // Делаем весь метод транзакционным
    @Transactional
    @Override
    public void processEvent(Map<String, Object> message, Acknowledgment ack, String topic) {
        log.error("📨 GET MESSAGE IN CONNECTOR CREATE EVENT {}", message); // Используйте log.info, а не log.error
        ConnectorCreateEvent conn = objectMapper.convertValue(message, ConnectorCreateEvent.class);

        // Логика теперь здесь
        if (repository.existsByConnectorIdAndChargeBoxId(conn.getConnectorId(), conn.getChargeBoxId())) {
            log.error("Connector with id {} already exists.", conn.getConnectorId());
            return; // Просто выходим, транзакция закроется
        }

        ConnectorEntity entity = new ConnectorEntity();
        entity.setChargeBoxId(conn.getChargeBoxId());
        entity.setConnectorId(conn.getConnectorId());
        entity.setInfo(conn.getInfo());
        entity.setActionType(conn.getActionType());
        entity.setVendorId(conn.getVendorId());
        entity.setCreatedAt(
                conn.getTimestamp() != null ? conn.getTimestamp() : Instant.now()
        );
        repository.save(entity);
        log.error("Saved new ChargeBox with id: {}", entity.getChargeBoxId());

        // Никакой ручной регистрации синхронизации не нужно!
    }
}
//{
//
//    private final ConnectorRepository repository;
//    private final ObjectMapper objectMapper;
//    private final ConnectorTransactionalService txService;
//
//    @Autowired
//    public ConnectorService(ConnectorRepository repository, ObjectMapper objectMapper, ConnectorTransactionalService txService) {
//        this.repository = repository;
//        this.objectMapper = objectMapper;
//        this.txService = txService;
//    }
//
//
////    @Transactional(propagation = Propagation.REQUIRED)
////    public int updateConnector(String chargeBoxId, int connectorId) {
////        return repository.nativeUpdateConnectorVersionAndChargeBoxVersion(connectorId, chargeBoxId);
////    }
//
//
////    @Transactional
////    public void saveConnectorIfNotExistsAndAck(ConnectorCreateEvent req, Acknowledgment ack) {
////        // проверка наличия
////        if (repository.existsByConnectorId(req.getConnectorId())) {
////            // ничего не сохраняем, но всё равно откатываем offset только после commit (в данном случае commit не изменит БД)
////            // зарегистрируем ack на afterCommit, чтобы offset был подтверждён когда TX закончится
////            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
////                @Override
////                public void afterCommit() {
////                    ack.acknowledge();
////                }
////            });
////            return;
////        }
////
////        ConnectorEntity entity = new ConnectorEntity();
////        entity.setChargeBoxId(req.getChargeBoxId());
////        entity.setConnectorId(req.getConnectorId());
////        entity.setInfo(req.getInfo());
////        entity.setActionType(req.getActionType());
////        entity.setCreatedAt(req.getTimestamp());
////        entity.setVendorId(req.getVendorId());
////        if (req.getTimestamp() != null) {
////            entity.setCreatedAt(req.getTimestamp());
////        } else {
////            entity.setCreatedAt(Instant.now());
////        }
////
////        repository.save(entity);
////
////        // подтверждаем offset только после успешного коммита транзакции
////        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
////            @Override
////            public void afterCommit() {
////                ack.acknowledge();
////            }
////        });
////    }
//
////    @Override
////    public void processEvent(Map<String, Object> message, Acknowledgment ack, String topic) {
////        ConnectorCreateEvent conn = objectMapper.convertValue(message, ConnectorCreateEvent.class);
////        saveConnectorIfNotExistsAndAck(conn, ack);
////    }
//
////    @Override
////    @Transactional
////    public void processEvent(
////            Map<String, Object> message,
////            Acknowledgment ack,
////            String topic
////    ) {
////        ConnectorCreateEvent conn =
////                objectMapper.convertValue(message, ConnectorCreateEvent.class);
////
////        if (repository.existsByConnectorId(conn.getConnectorId())) {
////
////            TransactionSynchronizationManager.registerSynchronization(
////                    new TransactionSynchronization() {
////                        @Override
////                        public void afterCommit() {
////                            ack.acknowledge();
////                        }
////                    }
////            );
////            return;
////        }
////
////        ConnectorEntity entity = new ConnectorEntity();
////        entity.setChargeBoxId(conn.getChargeBoxId());
////        entity.setConnectorId(conn.getConnectorId());
////        entity.setInfo(conn.getInfo());
////        entity.setActionType(conn.getActionType());
////        entity.setVendorId(conn.getVendorId());
////        entity.setCreatedAt(
////                conn.getTimestamp() != null ? conn.getTimestamp() : Instant.now()
////        );
////
////        repository.save(entity);
////
////        log.info("TX active: {}", TransactionSynchronizationManager.isActualTransactionActive());
////        log.info("Sync active: {}", TransactionSynchronizationManager.isSynchronizationActive());
////        TransactionSynchronizationManager.registerSynchronization(
////                new TransactionSynchronization() {
////                    @Override
////                    public void afterCommit() {
////                        ack.acknowledge();
////                    }
////                }
////        );
////    }
//
//    @Override
//    public void processEvent(Map<String, Object> message, Acknowledgment ack, String topic) {
//
//        log.error("📨 GET MESSAGE IN CONNECTOR CREATE EVENT {}", message);
//        ConnectorCreateEvent conn = objectMapper.convertValue(message, ConnectorCreateEvent.class);
//
//        txService.saveIfNotExists(conn, ack::acknowledge);
//    }
//}