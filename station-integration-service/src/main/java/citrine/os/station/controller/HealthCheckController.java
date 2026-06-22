package citrine.os.station.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контроллер для health checks от Consul
 *
 * Назначение: Предоставляет endpoint для проверки здоровья сервиса
 * Consul периодически проверяет этот endpoint для service discovery
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
public class HealthCheckController {

    private final HealthEndpoint healthEndpoint;

    /**
     * Health check endpoint который проверяет Consul
     */
    @GetMapping("/health")
    public ResponseEntity<HealthComponent> health() {
        HealthComponent health = healthEndpoint.health();
        log.debug("Health check: {}", health.getStatus());
        return ResponseEntity.ok(health);
    }

    /**
     * Deep health check с проверкой зависимостей
     */
    @GetMapping("/health/readiness")
    public ResponseEntity<HealthComponent> readiness() {
        // Здесь можно добавить проверки готовности (база данных, Redis, Kafka и т.д.)
        HealthComponent health = healthEndpoint.health();
        return ResponseEntity.ok(health);
    }

    /**
     * Liveness probe для Kubernetes/Consul
     */
    @GetMapping("/health/liveness")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("OK");
    }
}