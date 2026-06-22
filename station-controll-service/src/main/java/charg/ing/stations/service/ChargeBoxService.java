package charg.ing.stations.service;


import charg.ing.stations.dto.StationCreateEvent;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.service.interfaces.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Map;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@Slf4j
@Service
public class ChargeBoxService implements EventService {

    private final ChargeBoxRepository repository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ChargeBoxService(ChargeBoxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // Убираем этот метод, его логика теперь в processEvent
    // public void saveChargeBoxIfNotExistsAndAck(...) { ... }

    // Аннотация @Transactional теперь здесь!
    @Override
    @Transactional
    public void processEvent(Map<String, Object> message, Acknowledgment ack, String topic) {
        log.error("📨 Processing ChargeBox event: {}", message.get("chargeBoxId"));
        StationCreateEvent req = objectMapper.convertValue(message, StationCreateEvent.class);

        // Проверка наличия
        if (repository.existsByChargeBoxId(req.getChargeBoxId())) {
            log.error("ChargeBox with id {} already exists.", req.getChargeBoxId());
            return; // Просто выходим, транзакция успешно завершится
        }

        // Создание и сохранение новой сущности
        ChargeBoxEntity entity = new ChargeBoxEntity();
        entity.setChargeBoxId(req.getChargeBoxId());
        entity.setOcppProtocol(req.getOcppProtocol());
        entity.setChargePointVendor(req.getChargePointVendor());
        entity.setChargePointModel(req.getChargePointModel());
        entity.setChargePointSerialNumber(req.getChargePointSerialNumber());
        entity.setChargeBoxSerialNumber(req.getChargeBoxSerialNumber());
        entity.setFirmwareVersion(req.getFirmwareVersion());
        entity.setIccid(req.getIccid());
        entity.setImsi(req.getImsi());
        entity.setMeterType(req.getMeterType());
        entity.setMeterSerialNumber(req.getMeterSerialNumber());
        entity.setActionType(req.getActionType());
        if (req.getCreatedAt() != null) {
            entity.setCreatedAt(Instant.ofEpochMilli(req.getCreatedAt()));
        } else {
            entity.setCreatedAt(Instant.now());
        }

        repository.save(entity);
        log.error("Saved new ChargeBox with id: {}", req.getChargeBoxId());

        // Никакого вызова ack.acknowledge() и TransactionSynchronizationManager здесь нет!
    }
}
//{
//
//    private final ChargeBoxRepository repository;
//    private final ObjectMapper objectMapper;
//
//    @Autowired
//    public ChargeBoxService(ChargeBoxRepository repository, ObjectMapper objectMapper) {
//        this.repository = repository;
//        this.objectMapper = objectMapper;
//    }
//
//    @Transactional
//    public void saveChargeBoxIfNotExistsAndAck(StationCreateEvent req, Acknowledgment ack) {
//        // проверка наличия
//        if (repository.existsByChargeBoxId(req.getChargeBoxId())) {
//            // ничего не сохраняем, но всё равно откатываем offset только после commit (в данном случае commit не изменит БД)
//            // зарегистрируем ack на afterCommit, чтобы offset был подтверждён когда TX закончится
//            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
//                @Override
//                public void afterCommit() {
//                    ack.acknowledge();
//                }
//            });
//            return;
//        }
//
//        ChargeBoxEntity entity = new ChargeBoxEntity();
//        entity.setChargeBoxId(req.getChargeBoxId());
//        entity.setOcppProtocol(req.getOcppProtocol());
//        entity.setChargePointVendor(req.getChargePointVendor());
//        entity.setChargePointModel(req.getChargePointModel());
//        entity.setChargePointSerialNumber(req.getChargePointSerialNumber());
//        entity.setChargeBoxSerialNumber(req.getChargeBoxSerialNumber());
//        entity.setFirmwareVersion(req.getFirmwareVersion());
//        entity.setIccid(req.getIccid());
//        entity.setImsi(req.getImsi());
//        entity.setMeterType(req.getMeterType());
//        entity.setMeterSerialNumber(req.getMeterSerialNumber());
//        entity.setActionType(req.getActionType());
//        if (req.getCreatedAt() != null) {
//            entity.setCreatedAt(Instant.ofEpochMilli(req.getCreatedAt()));
//        } else {
//            entity.setCreatedAt(Instant.now());
//        }
//
//        repository.save(entity);
//
//        // подтверждаем offset только после успешного коммита транзакции
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
//            @Override
//            public void afterCommit() {
//                ack.acknowledge();
//            }
//        });
//    }
//
//    @Override
//    public void processEvent(Map<String, Object> message, Acknowledgment ack, String topic) {
//        StationCreateEvent req = objectMapper.convertValue(message, StationCreateEvent.class);
//        saveChargeBoxIfNotExistsAndAck(req, ack);
//    }
//}