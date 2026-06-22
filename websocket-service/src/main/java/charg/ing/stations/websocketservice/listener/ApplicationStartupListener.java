package charg.ing.stations.websocketservice.listener;

import charg.ing.stations.websocketservice.service.RedisStreamConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationStartupListener {

    private final RedisStreamConsumerService redisStreamConsumerService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=========================================");
        log.info("   WEBSOCKET SERVICE STARTING UP");
        log.info("=========================================");

        // Сначала проверяем подключение к Redis
        redisStreamConsumerService.checkConnection()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        connectionStatus -> {
                            Boolean connected = (Boolean) connectionStatus.get("connected");
                            if (Boolean.TRUE.equals(connected)) {
                                log.info("✅ Redis connection is OK");
                                initializeRedisStream();
                            } else {
                                log.error("❌ Redis connection check failed: {}",
                                        connectionStatus.get("error"));
                                log.warn("Service will start but Redis operations may fail");
                            }
                        },
                        error -> {
                            log.error("❌ Error checking Redis connection: {}", error.getMessage());
                            log.warn("Service will start but Redis operations may fail");
                        }
                );

        log.info("=========================================");
        log.info("   SERVICE STARTED SUCCESSFULLY");
        log.info("=========================================");
    }

    private void initializeRedisStream() {
        // Проверяем текущий статус consumer group
        redisStreamConsumerService.isOurConsumerGroupAvailable()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        groupAvailable -> {
                            if (groupAvailable) {
                                log.info("✅ Consumer group 'websocket-service' is already available");
                                // Все равно пытаемся "инициализировать" для установки флага
                                redisStreamConsumerService.ensureInitialized()
                                        .subscribe(initialized -> {
                                            if (initialized) {
                                                log.info("✅ Redis Stream consumer marked as initialized");
                                            }
                                        });
                            } else {
                                log.info("Consumer group 'websocket-service' is not available, attempting to create...");
                                redisStreamConsumerService.initialize()
                                        .subscribe(
                                                success -> {
                                                    if (success) {
                                                        log.info("✅ Redis Stream consumer initialized successfully");
                                                    } else {
                                                        log.error("❌ Failed to initialize Redis Stream consumer");
                                                    }
                                                },
                                                error -> {
                                                    log.error("💥 Error initializing Redis Stream consumer: {}",
                                                            error.getMessage(), error);
                                                }
                                        );
                            }
                        },
                        error -> {
                            log.error("❌ Error checking consumer group availability: {}", error.getMessage());
                        }
                );
    }
}
