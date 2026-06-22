package charg.ing.stations.service;

import charg.ing.stations.dto.TransactionResponseDTO;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.TransactionEntity;
import charg.ing.stations.enums.TransactionStatus;
import charg.ing.stations.repository.ChargeBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Останавливает активную транзакцию remote-командой STOP и проводит расчёт через
 * {@code TransactionService.updateStopTransactionAndAck}. Единая точка для всех автоматических
 * стопов — исчерпания бюджета ({@code MeterValueChargingConsumer}) и поломки коннектора
 * ({@code ConnectorStatusConsumer}); общий in-flight guard не даёт продублировать STOP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargingStopService {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(12);

    private final ChargeBoxRepository chargeBoxRepository;
    private final OcppRequestReplyService ocppRequestReplyService;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    /** Защита от повторной отправки STOP, пока предыдущий в полёте (события продолжают идти). */
    private final Set<String> stopping = ConcurrentHashMap.newKeySet();

    /**
     * Останавливает активную транзакцию. Возвращает {@code true}, если STOP был реально отправлен
     * (этот вызов выиграл guard), {@code false} — если стоп уже в процессе для этого коннектора.
     */
    public boolean stopActiveTransaction(TransactionEntity tx, String reason) {
        String key = tx.getChargeBoxId() + ":" + tx.getConnectorId();
        if (!stopping.add(key)) {
            return false; // стоп уже идёт для этого коннектора
        }
        try {
            log.info("Stopping tx {} (user {}) — reason: {}", tx.getTransactionId(), tx.getUserId(), reason);
            dispatchStop(tx);
            return true;
        } finally {
            stopping.remove(key);
        }
    }

    private void dispatchStop(TransactionEntity tx) {
        ChargeBoxEntity chargeBox = chargeBoxRepository.findByChargeBoxId(tx.getChargeBoxId());
        String ocppTag = chargeBox != null ? chargeBox.getOcppTag() : null;

        Map<String, Object> ocppRequest = new HashMap<>();
        ocppRequest.put("chargeBoxId", tx.getChargeBoxId());
        ocppRequest.put("connectorId", tx.getConnectorId());
        ocppRequest.put("ocppTag", ocppTag);

        Map<String, Object> responseMap = ocppRequestReplyService
                .sendAndReceive(ocppRequest, 10, true)
                .block(STOP_TIMEOUT);
        if (responseMap == null) {
            log.error("No OCPP stop response for tx {} — will retry on next trigger", tx.getTransactionId());
            return;
        }

        TransactionResponseDTO response = objectMapper.convertValue(responseMap, TransactionResponseDTO.class);
        response.setUserId(tx.getUserId());
        if (response.getStatus() == null) {
            response.setStatus(TransactionStatus.COMPLETED);
        }
        transactionService.updateStopTransactionAndAck(response, () -> {});
    }
}
