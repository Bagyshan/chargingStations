package charg.ing.stations.service;

import charg.ing.stations.dto.ConnectorCreateEvent;
import charg.ing.stations.dto.StationCreateEvent;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.service.interfaces.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Map;

@Service
@Transactional
public class ConnectorService implements EventService {

    private final ConnectorRepository repository;
    private final ChargeBoxRepository chargeBoxRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConnectorService(ConnectorRepository repository, ChargeBoxRepository chargeBoxRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.chargeBoxRepository = chargeBoxRepository;
        this.objectMapper = objectMapper;
    }


    @Transactional(propagation = Propagation.REQUIRED)
    public int updateConnector(String chargeBoxId, int connectorId) {
        return repository.nativeUpdateConnectorVersionAndChargeBoxVersion(connectorId, chargeBoxId);
    }


    @Transactional
    public void saveConnectorIfNotExistsAndAck(ConnectorCreateEvent req, Acknowledgment ack) {
        // проверка наличия
        if (repository.existsByConnectorId(req.getConnectorId())) {
            // ничего не сохраняем, но всё равно откатываем offset только после commit (в данном случае commit не изменит БД)
            // зарегистрируем ack на afterCommit, чтобы offset был подтверждён когда TX закончится
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ack.acknowledge();
                }
            });
            return;
        }

        ConnectorEntity entity = new ConnectorEntity();
        entity.setChargeBoxId(req.getChargeBoxId());
        entity.setConnectorId(req.getConnectorId());
        entity.setInfo(req.getInfo());
        entity.setActionType(req.getActionType());
        entity.setCreatedAt(req.getTimestamp());
        entity.setVendorId(req.getVendorId());
        if (req.getTimestamp() != null) {
            entity.setCreatedAt(req.getTimestamp());
        } else {
            entity.setCreatedAt(Instant.now());
        }

        repository.save(entity);

        // подтверждаем offset только после успешного коммита транзакции
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ack.acknowledge();
            }
        });
    }

    @Override
    public void processEvent(Map<String, Object> message, Acknowledgment ack, String topic) {
        ConnectorCreateEvent conn = objectMapper.convertValue(message, ConnectorCreateEvent.class);
        saveConnectorIfNotExistsAndAck(conn, ack);
    }
}