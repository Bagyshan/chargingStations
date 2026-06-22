package charg.ing.stations.health;

import charg.ing.stations.service.DataInitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("dataInitializationHealthIndicator")
@RequiredArgsConstructor
public class DataInitializationHealthIndicator implements HealthIndicator {

    private final DataInitializationService initializationService;

    @Override
    public Health health() {
        if (initializationService.isInitialized()) {
            return Health.up()
                    .withDetail("data-initialized", true)
                    .withDetail("service-status", "ready")
                    .build();
        } else {
            return Health.up()
                    .withDetail("data-initialized", false)
                    .withDetail("service-status", "initializing")
                    .build();
        }
    }
}