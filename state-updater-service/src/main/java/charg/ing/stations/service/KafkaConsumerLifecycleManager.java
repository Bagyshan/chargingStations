package charg.ing.stations.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class KafkaConsumerLifecycleManager {

    private final KafkaListenerEndpointRegistry registry;

    public void start() {
        registry.getListenerContainers().forEach(container -> {
            if (!container.isRunning()) {
                container.start();
                log.info("Kafka listener started");
            }
        });
    }

    public void stop() {
        registry.getListenerContainers().forEach(container -> {
            if (container.isRunning()) {
                container.stop();
                log.info("Kafka listener stopped");
            }
        });
    }
}
