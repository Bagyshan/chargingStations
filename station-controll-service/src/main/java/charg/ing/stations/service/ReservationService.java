package charg.ing.stations.service;


import charg.ing.stations.dto.event.BookingEventMessage;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.enums.ConnectorStatus;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class ReservationService {

    private final ConnectorRepository connectorRepository;
    private final ChargeBoxRepository chargeBoxRepository;
    private final StationStateService stationStateService;

    @Autowired
    public ReservationService(
            ConnectorRepository connectorRepository,
            ChargeBoxRepository chargeBoxRepository,
            StationStateService stationStateService
    ) {
        this.connectorRepository = connectorRepository;
        this.chargeBoxRepository = chargeBoxRepository;
        this.stationStateService = stationStateService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public void processReservationEvent(BookingEventMessage event, Acknowledgment ack) {

        log.info("Processing reservation event: {}", event);

        String chargeBoxId = event.getStationId();
        int connectorId = event.getConnectorId();

        // Блокируем записи для обновления (PESSIMISTIC_WRITE)
        ConnectorEntity connector = connectorRepository.findConnectorForUpdate(connectorId, chargeBoxId);
        if (connector == null) {
            throw new IllegalArgumentException("Connector not found: " + chargeBoxId + "-" + connectorId);
        }
        ChargeBoxEntity chargeBox = chargeBoxRepository.findChargeBoxForUpdate(chargeBoxId);
        if (chargeBox == null) {
            throw new IllegalArgumentException("ChargeBox not found: " + chargeBoxId);
        }

        String newStatus;
        String bookingUserId = null;

        if (event.getEventType() == BookingEventMessage.EventType.START_RESERVATION) {
            // Проверяем, что коннектор доступен для бронирования
            if (!ConnectorStatus.AVAILABLE.getValue().equals(connector.getStatus())) {
                throw new IllegalStateException("Connector is not available for reservation. Current status: " + connector.getStatus());
            }
            newStatus = ConnectorStatus.RESERVED.getValue();
            bookingUserId = event.getUserId().toString();
        } else if (event.getEventType() == BookingEventMessage.EventType.STOP_RESERVATION) {
            // При завершении бронирования очищаем userId. НО если на коннекторе уже идёт
            // зарядка (бронь была «поглощена» стартом зарядки — StartTransaction обнулил
            // bookingUserId), НЕ сбрасываем статус в AVAILABLE, иначе гонка событий затрёт
            // активную зарядку. В этом случае оставляем текущий статус (Charging).
            if (ConnectorStatus.CHARGING.getValue().equals(connector.getStatus())) {
                log.info("Connector {} is charging — STOP_RESERVATION keeps status CHARGING, only releases hold",
                        connectorId);
                newStatus = connector.getStatus();
            } else {
                newStatus = ConnectorStatus.AVAILABLE.getValue();
            }
            // Можно проверить, что bookingUserId совпадает, но для надёжности просто очищаем
            if (connector.getBookingUserId() != null && !connector.getBookingUserId().equals(event.getUserId().toString())) {
                log.warn("Connector {} was reserved by user {}, but stop event from user {} – will release anyway",
                        connectorId, connector.getBookingUserId(), event.getUserId());
            }
            bookingUserId = null;
        } else {
            throw new IllegalArgumentException("Unsupported event type: " + event.getEventType());
        }

        // Обновляем поля и увеличиваем версии
        connector.setStatus(newStatus);
        connector.setBookingUserId(bookingUserId);
        connector.setVersion(connector.getVersion() + 1);
        chargeBox.setVersion(chargeBox.getVersion() + 1);

        // Сохраняем
        connectorRepository.save(connector);
        chargeBoxRepository.save(chargeBox);

        // Публикуем обновление состояния станции (для WebSocket и других подписчиков)
        stationStateService.publishStationState(
                chargeBoxId,
                connectorId,
                newStatus,
                connector.getVersion(),
                chargeBox.getVersion()
        );

        // Подтверждаем сообщение Kafka только после успешного коммита транзакции
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ack.acknowledge();
                log.info("Reservation event processed and acknowledged: {}", event.getEventType());
            }
        });
    }
}