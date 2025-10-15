package charg.ing.stations.producer;

import charg.ing.stations.entity.StationStateOutbox;
import charg.ing.stations.repository.StationStateOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class StationStateOutboxPublisher {
//
//    private final StationStateOutboxRepository outboxRepository;
//    private final KafkaTemplate<String, Object> kafkaTemplate;
//    private final ObjectMapper objectMapper;
//
//    @Scheduled(fixedDelay = 3000)
//    @Transactional
//    public void publishOutboxMessages() {
//        List<StationStateOutbox> unpublishedMessages = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
//
//        log.info("Found {} unpublished messages", unpublishedMessages.size());
//
//        if (!unpublishedMessages.isEmpty()) {
//            List<Long> publishedIds = new ArrayList<>();
//
//            for (StationStateOutbox message : unpublishedMessages) {
//                try {
//                    log.info("Processing outbox message ID: {}, aggregate: {}", message.getId(), message.getAggregateId());
//
//                    Map<String, Object> payload = objectMapper.readValue(message.getPayload(),
//                            new TypeReference<Map<String, Object>>() {});
//
//                    log.info("Payload: {}", payload);
//
//                    kafkaTemplate.send("station.state",
//                                message.getAggregateId(),
//                                payload)
//                                .get(5, TimeUnit.SECONDS); // Ждем подтверждения
//
//                    publishedIds.add(message.getId());
//
//                } catch (Exception e) {
//                    log.error("Failed to publish outbox message: {}", message.getId(), e);
//                }
//            }
//
//            if (!publishedIds.isEmpty()) {
//                outboxRepository.markAsPublished(publishedIds, Instant.now());
//            }
//        }
//    }
//}

//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class StationStateOutboxPublisher {
//    private final StationStateOutboxRepository outboxRepository;
//    private final KafkaTemplate<String, Object> kafkaTemplate;
//    private final ObjectMapper objectMapper;
//
//    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
//    public void publishOutboxMessages() {
//        try {
//            List<StationStateOutbox> unpublishedMessages = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
//            log.info("Found {} unpublished messages", unpublishedMessages.size());
//
//            if (!unpublishedMessages.isEmpty()) {
//                List<Long> publishedIds = new ArrayList<>();
//                List<CompletableFuture<Void>> futures = new ArrayList<>();
//
//                for (StationStateOutbox message : unpublishedMessages) {
//                    try {
//                        log.info("Processing outbox message ID: {}, aggregate: {}",
//                                message.getId(), message.getAggregateId());
//
//                        Object payloadValue;
//                        if (message.getPayload() instanceof String) {
//                            payloadValue = objectMapper.readValue((String) message.getPayload(),
//                                    new TypeReference<Map<String, Object>>() {});
//                        } else {
//                            payloadValue = message.getPayload();
//                        }
//
//                        log.info("Sending to Kafka topic 'station.state' with key: {}", message.getAggregateId());
//                        log.debug("Kafka message payload: {}", payloadValue);
//
//                        CompletableFuture<Void> future = kafkaTemplate.send("station.state",
//                                        message.getAggregateId(),
//                                        payloadValue)
//                                .thenAccept(sendResult -> {
//                                    log.info("Message sent successfully to topic {} partition {} offset {}",
//                                            sendResult.getRecordMetadata().topic(),
//                                            sendResult.getRecordMetadata().partition(),
//                                            sendResult.getRecordMetadata().offset());
//                                    publishedIds.add(message.getId());
//                                })
//                                .exceptionally(e -> {
//                                    log.error("Failed to send message to Kafka for outbox ID: {}", message.getId(), e);
//                                    return null;
//                                });
//
//                        futures.add(future);
//
//                    } catch (JsonProcessingException e) {
//                        log.error("Failed to parse payload for outbox message ID: {}", message.getId(), e);
//                    } catch (Exception e) {
//                        log.error("Failed to process outbox message ID: {}", message.getId(), e);
//                    }
//                }
//
//                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
//                        .get(10, TimeUnit.SECONDS);
//
//                if (!publishedIds.isEmpty()) {
//                    log.info("Marking {} messages as published", publishedIds.size());
//                    outboxRepository.markAsPublished(publishedIds, Instant.now());
//                }
//            }
//        } catch (Exception e) {
//            log.error("Error in publishOutboxMessages", e);
//        }
//    }
//}


//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class StationStateOutboxPublisher {
//    private final StationStateOutboxRepository outboxRepository;
//    private final KafkaTemplate<String, Object> kafkaTemplate;
//    private final ObjectMapper objectMapper;
//
//    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
//    @Transactional
//    public void publishOutboxMessages() {
//        log.info("Starting outbox message publishing process at {}", Instant.now());
//
//        try {
//            List<StationStateOutbox> unpublishedMessages = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
//            log.info("Found {} unpublished messages", unpublishedMessages.size());
//
//            if (unpublishedMessages.isEmpty()) {
//                log.debug("No unpublished messages found");
//                return;
//            }
//
//            List<Long> publishedIds = new ArrayList<>();
//            AtomicInteger successCount = new AtomicInteger(0);
//            AtomicInteger failureCount = new AtomicInteger(0);
//
//            unpublishedMessages.forEach(message -> {
//                try {
//                    log.info("Processing message ID: {}, aggregate: {}", message.getId(), message.getAggregateId());
//
//                    // Преобразование payload
//                    Object payload = convertPayload(message.getPayload());
//                    log.debug("Converted payload: {}", payload);
//
//                    // Отправка в Kafka
//                    SendResult<String, Object> result = kafkaTemplate.send(
//                                    "station.state",
//                                    message.getAggregateId(),
//                                    payload)
//                            .get(5, TimeUnit.SECONDS);
//
//                    log.info("Message sent successfully to partition {} offset {}",
//                            result.getRecordMetadata().partition(),
//                            result.getRecordMetadata().offset());
//
//                    publishedIds.add(message.getId());
//                    successCount.incrementAndGet();
//
//                } catch (Exception e) {
//                    failureCount.incrementAndGet();
//                    log.error("Failed to publish message ID: {}", message.getId(), e);
//                }
//            });
//
//            if (!publishedIds.isEmpty()) {
//                log.info("Marking {} messages as published", publishedIds.size());
//                outboxRepository.markAsPublished(publishedIds, Instant.now());
//            }
//
//            log.info("Publishing completed. Success: {}, Failures: {}",
//                    successCount.get(), failureCount.get());
//
//        } catch (Exception e) {
//            log.error("Error during outbox publishing process", e);
//        }
//    }
//
//    private Object convertPayload(Object payload) throws JsonProcessingException {
//        if (payload instanceof String) {
//            return objectMapper.readValue((String) payload, new TypeReference<Map<String, Object>>() {});
//        }
//        return payload;
//    }
//}



@Component
@RequiredArgsConstructor
@Slf4j
public class StationStateOutboxPublisher {
    private final StationStateOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
    public void publishOutboxMessages() {
        log.info("Starting outbox message publishing process at {}", Instant.now());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        transactionTemplate.execute(status -> {
            try {
                List<StationStateOutbox> unpublishedMessages = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
                log.info("Found {} unpublished messages", unpublishedMessages.size());

                if (unpublishedMessages.isEmpty()) {
                    log.debug("No unpublished messages found");
                    return null;
                }

                List<Long> publishedIds = new ArrayList<>();
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failureCount = new AtomicInteger(0);

                unpublishedMessages.forEach(message -> {
                    try {
                        log.info("Processing message ID: {}, aggregate: {}", message.getId(), message.getAggregateId());

                        Object payload = convertPayload(message.getPayload());
                        log.debug("Converted payload: {}", payload);

                        SendResult<String, Object> result = kafkaTemplate.send(
                                        "station.state",
                                        message.getAggregateId(),
                                        payload)
                                .get(5, TimeUnit.SECONDS);

                        log.info("Message sent successfully to partition {} offset {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());

                        publishedIds.add(message.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.error("Failed to publish message ID: {}", message.getId(), e);
                    }
                });

                if (!publishedIds.isEmpty()) {
                    log.info("Marking {} messages as published", publishedIds.size());
                    outboxRepository.markAsPublished(publishedIds, Instant.now());
                }

                log.info("Publishing completed. Success: {}, Failures: {}",
                        successCount.get(), failureCount.get());

                return null;
            } catch (Exception e) {
                log.error("Error during outbox publishing process", e);
                status.setRollbackOnly();
                return null;
            }
        });
    }

    private Object convertPayload(Object payload) throws JsonProcessingException {
        if (payload instanceof String) {
            return objectMapper.readValue((String) payload, new TypeReference<Map<String, Object>>() {});
        }
        return payload;
    }
}