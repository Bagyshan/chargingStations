package charg.ing.stations.service;

import charg.ing.stations.dto.kafka.ChargingPaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka request-reply client for the user's wallet balance (payment-service), used to size the kWh
 * budget of a charging session. Mirrors {@link OcppRequestReplyService}: correlate by requestId,
 * reply consumed from {@code charging.payment.responses} as a Map.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargingBalanceClient {

    private static final String REQUEST_TOPIC = "charging.payment.requests";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Map<UUID, MonoSink<Map<String, Object>>> pendingRequests = new ConcurrentHashMap<>();

    /** Blocking balance lookup. Returns the wallet balance, or throws on timeout / failure. */
    public BigDecimal getBalance(UUID userId, Duration timeout) {
        Map<String, Object> response = requestBalance(userId).block(timeout);
        if (response == null) {
            throw new IllegalStateException("No balance response for user " + userId);
        }
        if (!Boolean.TRUE.equals(response.get("success"))) {
            throw new IllegalStateException("Balance lookup failed for user " + userId
                    + ": " + response.get("errorMessage"));
        }
        Object balance = response.get("balance");
        if (balance == null) {
            throw new IllegalStateException("Balance lookup returned null for user " + userId);
        }
        return new BigDecimal(balance.toString());
    }

    private Mono<Map<String, Object>> requestBalance(UUID userId) {
        UUID requestId = UUID.randomUUID();
        ChargingPaymentRequest request = new ChargingPaymentRequest(requestId, userId);

        Mono<Map<String, Object>> responseMono = Mono.create(sink -> pendingRequests.put(requestId, sink));

        kafkaTemplate.send(REQUEST_TOPIC, requestId.toString(), request)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        MonoSink<Map<String, Object>> sink = pendingRequests.remove(requestId);
                        if (sink != null) {
                            sink.error(new RuntimeException("Failed to send charging payment request", ex));
                        }
                    }
                });

        return responseMono.doOnError(e -> pendingRequests.remove(requestId));
    }

    @KafkaListener(
            topics = "charging.payment.responses",
            groupId = "station-controller-service-group-charging",
            containerFactory = "ocppResponseListenerContainerFactory"
    )
    @SuppressWarnings("unchecked")
    public void onBalanceResponse(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof Map)) {
            return;
        }
        Map<String, Object> response = (Map<String, Object>) record.value();
        Object requestIdStr = response.get("requestId");
        if (requestIdStr == null) {
            return;
        }
        try {
            UUID requestId = UUID.fromString(requestIdStr.toString());
            MonoSink<Map<String, Object>> sink = pendingRequests.remove(requestId);
            if (sink != null) {
                sink.success(response);
            } else {
                log.warn("No pending charging balance request for requestId: {}", requestId);
            }
        } catch (Exception e) {
            log.error("Invalid requestId in charging balance response: {}", requestIdStr, e);
        }
    }
}
