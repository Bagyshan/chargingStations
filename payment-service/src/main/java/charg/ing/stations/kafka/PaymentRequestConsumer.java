package charg.ing.stations.kafka;

import charg.ing.stations.dto.kafka.PaymentRequest;
import charg.ing.stations.dto.kafka.PaymentResponse;
import charg.ing.stations.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestConsumer {

    private final UserBalanceRepository balanceRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "booking.payment.requests", groupId = "payment-service-group")
    public void handlePaymentRequest(PaymentRequest request) {
        log.info("Received payment request: {}", request);

        balanceRepository.findByUserId(request.getUserId())
                .map(userBalance -> {

                    // Проверка: пользователь уже имеет активное бронирование
                    if (Boolean.TRUE.equals(userBalance.isBooking())) {
                        return new PaymentResponse(
                                request.getRequestId(),
                                request.getUserId(),
                                userBalance.getBalance(),
                                false,
                                "Пользователь уже бронирует станцию"
                        );
                    }


                    return new PaymentResponse(
                        request.getRequestId(),
                        request.getUserId(),
                        userBalance.getBalance(),
                        true,
                        null
                );
                })
                .defaultIfEmpty(new PaymentResponse(
                        request.getRequestId(),
                        request.getUserId(),
                        null,
                        false,
                        "User balance not found"
                ))
                .subscribe(response -> {
                    kafkaTemplate.send("booking.payment.responses", response.getRequestId().toString(), response);
                    log.info("Sent payment response: {}", response);
                });
    }
}