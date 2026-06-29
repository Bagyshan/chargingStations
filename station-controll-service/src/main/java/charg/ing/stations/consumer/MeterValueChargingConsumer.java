package charg.ing.stations.consumer;

import charg.ing.stations.dto.event.ChargingStatusEvent;
import charg.ing.stations.dto.meter.MeterValueMessage;
import charg.ing.stations.dto.meter.PayloadItem;
import charg.ing.stations.dto.meter.SampledValue;
import charg.ing.stations.entity.TransactionEntity;
import charg.ing.stations.enums.TransactionStatus;
import charg.ing.stations.producer.ChargingStatusProducer;
import charg.ing.stations.repository.TransactionRepository;
import charg.ing.stations.service.ChargingStopService;
import charg.ing.stations.service.StationConnectivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enforces the prepaid kWh budget while charging: tracks the cumulative energy register from
 * {@code station.meter.values} and, once consumed kWh reaches the transaction's
 * {@code max_kw_quantity}, dispatches a remote STOP_TRANSACTION (via {@link ChargingStopService}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeterValueChargingConsumer {

    private static final String ENERGY_MEASURAND = "ENERGY_ACTIVE_IMPORT_REGISTER";
    private static final String SOC_MEASURAND = "SO_C";

    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;
    private final ChargingStatusProducer chargingStatusProducer;
    private final ChargingStopService chargingStopService;
    private final StationConnectivityService connectivityService;

    @KafkaListener(topics = "station.meter.values", groupId = "station-controller-service-group-meter")
    public void onMeterValue(Map<String, Object> value, Acknowledgment ack) {
        try {
            MeterValueMessage msg = objectMapper.convertValue(value, MeterValueMessage.class);
            if (msg.getChargeBoxId() == null || msg.getConnectorId() == null) {
                return;
            }

            // Поток meter-значений идёт только при активной зарядке — станция гарантированно на связи.
            connectivityService.markSeen(msg.getChargeBoxId(), Instant.now());

            Optional<TransactionEntity> active = transactionRepository
                    .findFirstByChargeBoxIdAndConnectorIdAndStatusOrderByIdDesc(
                            msg.getChargeBoxId(), msg.getConnectorId(), TransactionStatus.ACTIVE);
            if (active.isEmpty()) {
                return; // not an active session — nothing to push or enforce
            }
            TransactionEntity tx = active.get();

            Double registerWh = extractEnergyWh(msg);
            Double soc = extractSoc(msg);

            // 1. Push live status to the initiating user (every meter value of an active session).
            publishStatus(tx, registerWh, soc);

            // 2. Enforce the prepaid kWh budget (auto-stop) when an energy register is present.
            if (registerWh != null && tx.getMaxKwQuantity() != null) {
                enforceBudget(tx, registerWh);
            }
        } catch (Exception e) {
            log.error("Failed to process meter value: {}", e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }

    private void publishStatus(TransactionEntity tx, Double registerWh, Double soc) {
        if (tx.getUserId() == null) {
            return; // charger-initiated session without a known initiator
        }
        BigDecimal energyKwh = null;
        BigDecimal currentCost = null;
        if (registerWh != null) {
            int startValue = tx.getStartValue() != null ? tx.getStartValue() : 0;
            double consumedWh = registerWh - startValue;
            if (consumedWh < 0) consumedWh = 0;
            energyKwh = BigDecimal.valueOf(consumedWh / 1000.0).setScale(3, RoundingMode.HALF_UP);
            if (tx.getPricePerKwh() != null) {
                currentCost = energyKwh.multiply(tx.getPricePerKwh()).setScale(2, RoundingMode.HALF_UP);
            }
        }

        chargingStatusProducer.publish(ChargingStatusEvent.builder()
                .userId(tx.getUserId())
                .chargeBoxId(tx.getChargeBoxId())
                .connectorId(tx.getConnectorId())
                .transactionId(tx.getTransactionId())
                .energyKwh(energyKwh)
                .currentCost(currentCost)
                .kwCost(tx.getPricePerKwh())
                .maxKwQuantity(tx.getMaxKwQuantity())
                .startedAt(tx.getStartTimestamp())
                .soc(soc)
                .status(tx.getStatus() != null ? tx.getStatus().name() : null)
                .timestamp(Instant.now())
                .build());
    }

    private void enforceBudget(TransactionEntity tx, double registerWh) {
        int startValue = tx.getStartValue() != null ? tx.getStartValue() : 0;
        double consumedWh = registerWh - startValue;
        if (consumedWh < 0) consumedWh = 0;
        double maxWh = tx.getMaxKwQuantity().doubleValue() * 1000.0;

        if (consumedWh >= maxWh) {
            chargingStopService.stopActiveTransaction(tx, String.format(
                    "budget exhausted: consumed %.0f Wh >= max %.0f Wh", consumedWh, maxWh));
        }
    }

    private Double extractEnergyWh(MeterValueMessage msg) {
        if (msg.getPayload() == null) {
            return null;
        }
        Double latest = null;
        for (PayloadItem item : msg.getPayload()) {
            List<SampledValue> samples = item.getSampledValue();
            if (samples == null) continue;
            for (SampledValue sv : samples) {
                if (!ENERGY_MEASURAND.equals(sv.getMeasurand()) || sv.getValue() == null) {
                    continue;
                }
                try {
                    double wh = Double.parseDouble(sv.getValue().trim());
                    if (isKilowattHour(sv.getUnit())) {
                        wh *= 1000.0; // normalize kWh -> Wh
                    }
                    latest = wh; // cumulative register; the last sample is the most recent reading
                } catch (NumberFormatException ignored) {
                    // skip malformed value
                }
            }
        }
        return latest;
    }

    /** Handles the various ways stations spell kWh: "KWH", "K_WH", "kWh", "Wh", etc. */
    private boolean isKilowattHour(String unit) {
        if (unit == null) {
            return false;
        }
        String normalized = unit.replace("_", "").replace("-", "").toUpperCase();
        return normalized.equals("KWH");
    }

    private Double extractSoc(MeterValueMessage msg) {
        if (msg.getPayload() == null) {
            return null;
        }
        Double latest = null;
        for (PayloadItem item : msg.getPayload()) {
            List<SampledValue> samples = item.getSampledValue();
            if (samples == null) continue;
            for (SampledValue sv : samples) {
                if (!SOC_MEASURAND.equals(sv.getMeasurand()) || sv.getValue() == null) {
                    continue;
                }
                try {
                    latest = Double.parseDouble(sv.getValue().trim());
                } catch (NumberFormatException ignored) {
                    // skip malformed value
                }
            }
        }
        return latest;
    }
}
