package charg.ing.stations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaRequestReplyService {

    private final KafkaSender<String, Object> kafkaSender;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Sinks.One<Object>> pendingRequests = new ConcurrentHashMap<>();

    // Брокер берём из конфигурации (env SPRING_KAFKA_BOOTSTRAPSERVERS), а не хардкодим localhost.
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public <T> Mono<T> sendAndReceive(String requestTopic, String responseTopic, Object request, UUID requestId, Class<T> responseClass, Duration timeout) {
//        UUID requestId = UUID.randomUUID();
//        Sinks.One<Object> sink = Sinks.one();
//        pendingRequests.put(requestId, sink);
        Sinks.One<Object> sink = Sinks.one();
        pendingRequests.put(requestId, sink);

        // Отправляем запрос
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(requestTopic, requestId.toString(), request);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, requestId.toString());

        return kafkaSender.send(Mono.just(senderRecord))
                .next()
                .flatMap(result -> {
                    log.info("Request sent with id: {}", requestId);
                    // Ждём ответ
                    return sink.asMono()
                            .timeout(timeout)
                            .doOnError(e -> {
                                pendingRequests.remove(requestId);
                                log.error("Timeout or error waiting for response for requestId: {}", requestId, e);
                            })
                            .map(response -> {
                                pendingRequests.remove(requestId);
                                return responseClass.cast(response);
                            });
                });
    }

    @PostConstruct
    public void registerResponseListener() {
        log.info("Registering Kafka response listener...");
        ReceiverOptions<String, Object> receiverOptions = ReceiverOptions.<String, Object>create(Map.of(
                "bootstrap.servers", bootstrapServers,
                "group.id", "booking-service-response-listener-v2",
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.json.trusted.packages", "*"
        )).subscription(java.util.List.of("booking.payment.responses", "booking.station.responses"));

        KafkaReceiver.create(receiverOptions)
                .receive()
                .doOnNext(record -> {
                    String key = record.key();
                    Object value = record.value();
                    try {
                        UUID requestId = UUID.fromString(key);
                        log.debug("Received response for requestId: {}", requestId);
                        Sinks.One<Object> sink = pendingRequests.remove(requestId);
                        if (sink != null) {
                            log.debug("Found pending request for requestId: {}. Emitting value.", requestId);
                            sink.tryEmitValue(value);
                        } else {
                            log.warn("No pending request found for id: {}. This might be an old or duplicate message.", requestId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to process response record with key: {}", key, e);
                    } finally {
                        // ВАЖНО: Всегда подтверждаем обработку сообщения, чтобы offset двигался
                        record.receiverOffset().acknowledge();
                    }
                })
                .doOnError(e -> log.error("Error in Kafka response listener", e))
                .subscribe();
    }
}