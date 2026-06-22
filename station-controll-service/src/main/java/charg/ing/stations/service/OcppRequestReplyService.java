package charg.ing.stations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
//import org.springframework.kafka.core.SendResult;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcppRequestReplyService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Map<UUID, MonoSink<Map<String, Object>>> pendingRequests = new ConcurrentHashMap<>();

    public Mono<Map<String, Object>> sendAndReceive(Map<String, Object> request, long timeoutSeconds, boolean isStop) {
//        UUID correlationId = UUID.randomUUID();
//        request.put("correlationId", correlationId.toString()); // добавляем correlationId в запрос
//
//        Mono<Map<String, Object>> responseMono = Mono.create(sink -> {
//            pendingRequests.put(correlationId, sink);
//            kafkaTemplate.send("ocpp.requests", (String) request.get("chargeBoxId"), request)
//                    .addCallback(
//                            result -> log.debug("Sent OCPP request with correlationId: {}", correlationId),
//                            ex -> {
//                                pendingRequests.remove(correlationId);
//                                sink.error(new RuntimeException("Failed to send OCPP request", ex));
//                            }
//                    );
//        });
//
//        return responseMono
//                .timeout(Duration.ofSeconds(timeoutSeconds))
//                .doOnError(e -> pendingRequests.remove(correlationId));
        UUID correlationId = UUID.randomUUID();
        request.put("correlationId", correlationId.toString());
        request.put("isStop", isStop);

        // 1. Создаем Mono, который будет завершен слушателем при получении ответа
        Mono<Map<String, Object>> responseMono = Mono.create(sink -> {
            pendingRequests.put(correlationId, sink);
            // Ничего больше не делаем здесь. Ждем ответа.
        });

        // 2. Отправляем сообщение и получаем CompletableFuture
        CompletableFuture<SendResult<String, Object>> sendFuture = kafkaTemplate.send(
                "ocpp.requests",
                (String) request.get("chargeBoxId"),
                request
        );

        // 3. Добавляем обработчик ошибок к CompletableFuture
        sendFuture.exceptionally(ex -> {
            log.error("Failed to send OCPP request with correlationId: {}", correlationId, ex);
            // Если отправка не удалась, извлекаем sink и завершаем его с ошибкой
            MonoSink<Map<String, Object>> sink = pendingRequests.remove(correlationId);
            if (sink != null) {
                sink.error(new RuntimeException("Failed to send OCPP request", ex));
            }
            // exceptionally() должен возвращать значение, возвращаем null
            return null;
        });

        // 4. Возвращаем Mono, который будет ожидать ответ или таймаут
        return responseMono
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnError(e -> {
                    // Если произошел таймаут, удаляем запрос из карты
                    pendingRequests.remove(correlationId);
                });
    }

    @KafkaListener(
            topics = "ocpp.responses",
            groupId = "station-controller-service-group-ocpp",
            containerFactory = "ocppResponseListenerContainerFactory"
    )
    public void onOcppResponse(ConsumerRecord<String, Object> record) {
        if (record.value() instanceof Map) {
            Map<String, Object> response = (Map<String, Object>) record.value();
            String correlationIdStr = (String) response.get("correlationId");
            if (correlationIdStr != null) {
                try {
                    UUID correlationId = UUID.fromString(correlationIdStr);
                    MonoSink<Map<String, Object>> sink = pendingRequests.remove(correlationId);
                    if (sink != null) {
                        sink.success(response);
                    } else {
                        log.warn("No pending request for correlationId: {}", correlationId);
                    }
                } catch (Exception e) {
                    log.error("Invalid correlationId: {}", correlationIdStr, e);
                }
            }
        }
    }
}