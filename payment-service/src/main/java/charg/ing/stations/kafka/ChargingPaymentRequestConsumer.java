package charg.ing.stations.kafka;

import charg.ing.stations.dto.kafka.ChargingPaymentRequest;
import charg.ing.stations.dto.kafka.ChargingPaymentResponse;
import charg.ing.stations.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Answers balance lookups from station-controll-service before a charging session starts.
 * Request-reply correlated by {@code requestId} on {@code charging.payment.requests} /
 * {@code charging.payment.responses} (mirrors the booking saga).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChargingPaymentRequestConsumer {

    private static final String RESPONSE_TOPIC = "charging.payment.responses";

    private final UserBalanceRepository balanceRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "charging.payment.requests", groupId = "payment-service-group")
    public void handleChargingPaymentRequest(ChargingPaymentRequest request) {
        log.info("Received charging payment request: {}", request);

        balanceRepository.findByUserId(request.getUserId())
                .map(balance -> new ChargingPaymentResponse(
                        request.getRequestId(),
                        request.getUserId(),
                        balance.getBalance(),
                        true,
                        null))
                .defaultIfEmpty(new ChargingPaymentResponse(
                        request.getRequestId(),
                        request.getUserId(),
                        null,
                        false,
                        "User balance not found"))
                .subscribe(response -> {
                    kafkaTemplate.send(RESPONSE_TOPIC, response.getRequestId().toString(), response);
                    log.info("Sent charging payment response: {}", response);
                });
    }
}
