package charg.ing.stations.service;

import charg.ing.stations.dto.availability.AvailabilityResult;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.enums.ConnectorStatus;
import charg.ing.stations.enums.ServiceStatus;
import charg.ing.stations.enums.UnavailabilityReason;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Единая точка проверки доступности станции/коннектора для брони и зарядки — закрывает
 * «unhappy case» сломанных (OCPP {@code Faulted}/{@code Unavailable}) и административно
 * выключенных ({@link ServiceStatus#OUT_OF_SERVICE}/{@link ServiceStatus#MAINTENANCE}) станций.
 *
 * <p>Используется и сагой бронирования ({@code BookingRequestConsumer}), и прямым стартом
 * зарядки ({@code StationController.startTransaction}), чтобы правило доступности было одно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationAvailabilityService {

    private final ChargeBoxRepository chargeBoxRepository;
    private final ConnectorRepository connectorRepository;

    /** Сколько нельзя бронировать коннектор после окончания последней зарядки. */
    private static final Duration BOOKING_COOLDOWN = Duration.ofMinutes(10);

    /**
     * Можно ли пользователю {@code userId} запустить зарядку на коннекторе. Блокирует только
     * однозначно «плохие» состояния (станция выключена / коннектор Faulted|Unavailable / коннектор
     * забронирован другим пользователем) — чтобы не ломать рабочие сценарии (Preparing и т.п.).
     */
    @Transactional(readOnly = true)
    public AvailabilityResult checkChargeable(String chargeBoxId, Integer connectorId, String userId) {
        ChargeBoxEntity box = chargeBoxRepository.findByChargeBoxId(chargeBoxId);
        if (box == null) {
            return AvailabilityResult.deny(UnavailabilityReason.STATION_NOT_FOUND, "Station not found");
        }
        AvailabilityResult admin = checkServiceStatus(box);
        if (!admin.available()) {
            return admin;
        }

        ConnectorEntity connector = connectorRepository
                .findByChargeBoxIdAndConnectorId(chargeBoxId, connectorId)
                .orElse(null);
        if (connector == null) {
            return AvailabilityResult.deny(UnavailabilityReason.CONNECTOR_NOT_FOUND, "Connector not found");
        }
        if (isOutOfOrder(connector.getStatus())) {
            return AvailabilityResult.deny(UnavailabilityReason.NOT_OPERATIONAL,
                    "Connector is out of order (" + connector.getStatus() + ")");
        }
        if (connector.getBookingUserId() != null && !connector.getBookingUserId().equals(userId)) {
            return AvailabilityResult.deny(UnavailabilityReason.RESERVED_BY_OTHER,
                    "Connector is reserved by another user");
        }
        return AvailabilityResult.ok();
    }

    /**
     * Можно ли забронировать коннектор: станция в работе, коннектор именно {@code Available} и
     * ещё никем не забронирован.
     */
    @Transactional(readOnly = true)
    public AvailabilityResult checkBookable(String chargeBoxId, Integer connectorId) {
        ChargeBoxEntity box = chargeBoxRepository.findByChargeBoxId(chargeBoxId);
        if (box == null) {
            return AvailabilityResult.deny(UnavailabilityReason.STATION_NOT_FOUND, "Station not found");
        }
        AvailabilityResult admin = checkServiceStatus(box);
        if (!admin.available()) {
            return admin;
        }

        ConnectorEntity connector = connectorRepository
                .findByChargeBoxIdAndConnectorId(chargeBoxId, connectorId)
                .orElse(null);
        if (connector == null) {
            return AvailabilityResult.deny(UnavailabilityReason.CONNECTOR_NOT_FOUND, "Connector not found");
        }
        if (!ConnectorStatus.AVAILABLE.getValue().equalsIgnoreCase(connector.getStatus())) {
            return AvailabilityResult.deny(UnavailabilityReason.NOT_OPERATIONAL, "Connector is not available");
        }
        if (connector.getBookingUserId() != null) {
            return AvailabilityResult.deny(UnavailabilityReason.ALREADY_RESERVED, "Connector is already reserved");
        }
        // Кулдаун: коннектор нельзя бронировать в течение BOOKING_COOLDOWN после окончания
        // последней зарядки (даёт время освободить место / отъехать предыдущему авто).
        Instant lastChargeEnd = connector.getLastChargingEndedAt();
        if (lastChargeEnd != null) {
            Duration sinceCharge = Duration.between(lastChargeEnd, Instant.now());
            if (sinceCharge.compareTo(BOOKING_COOLDOWN) < 0) {
                long waitMinutes = BOOKING_COOLDOWN.minus(sinceCharge).toMinutes() + 1;
                // Маркер "COOLDOWN <минут>" — стабильный признак для клиента: мобильное
                // приложение по нему показывает всплывающее окно с числом минут ожидания.
                return AvailabilityResult.deny(UnavailabilityReason.COOLDOWN,
                        "COOLDOWN " + waitMinutes + " min — connector was recently charging");
            }
        }
        return AvailabilityResult.ok();
    }

    private AvailabilityResult checkServiceStatus(ChargeBoxEntity box) {
        ServiceStatus status = box.getServiceStatus();
        if (status != null && status != ServiceStatus.IN_SERVICE) {
            return AvailabilityResult.deny(UnavailabilityReason.OUT_OF_SERVICE,
                    "Station is " + status.name().toLowerCase().replace('_', ' '));
        }
        // offline (нет связи по OCPP-websocket) — заряжать/бронировать нельзя.
        if (Boolean.FALSE.equals(box.getOnline())) {
            return AvailabilityResult.deny(UnavailabilityReason.OFFLINE, "Station is offline");
        }
        return AvailabilityResult.ok();
    }

    /** Сломанные/выведенные станцией состояния OCPP, при которых нельзя стартовать зарядку. */
    private boolean isOutOfOrder(String status) {
        return ConnectorStatus.FAULTED.getValue().equalsIgnoreCase(status)
                || ConnectorStatus.UNAVAILABLE.getValue().equalsIgnoreCase(status);
    }
}
