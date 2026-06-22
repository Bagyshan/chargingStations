package charg.ing.stations.controller;

import charg.ing.stations.service.DataInitializationService;
import charg.ing.stations.service.util.KafkaMessageProcessor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/state-updater")
@RequiredArgsConstructor
@Slf4j
public class StateUpdaterController {

    private final DataInitializationService initializationService;
    private final KafkaMessageProcessor kafkaMessageProcessor;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/status")
    public ResponseEntity<ServiceStatus> getStatus() {
        ServiceStatus status = new ServiceStatus();
        status.setInitialized(initializationService.isInitialized());
        status.setKafkaProcessingEnabled(kafkaMessageProcessor.isEnabled());
        status.setKafkaProcessing(kafkaMessageProcessor.isProcessing());
        return ResponseEntity.ok(status);
    }

    @PostMapping("/reload")
    public Mono<ResponseEntity<String>> reloadData() {
        log.info("Manual data reload requested");
        return initializationService.reloadData()
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok("Data reload initiated successfully");
                    } else {
                        return ResponseEntity.status(500).body("Failed to reload data");
                    }
                });
    }

    @PostMapping("/kafka/enable")
    public ResponseEntity<String> enableKafkaProcessing() {
        kafkaMessageProcessor.setEnabled(true);
        return ResponseEntity.ok("Kafka processing enabled");
    }

    @PostMapping("/kafka/disable")
    public ResponseEntity<String> disableKafkaProcessing() {
        kafkaMessageProcessor.setEnabled(false);
        return ResponseEntity.ok("Kafka processing disabled");
    }

    @Data
    private static class ServiceStatus {
        private boolean initialized;
        private boolean kafkaProcessingEnabled;
        private boolean kafkaProcessing;
    }
}