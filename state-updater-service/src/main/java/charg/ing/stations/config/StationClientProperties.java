package charg.ing.stations.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки исходящего клиента к station-controll-service.
 *
 * <p>Как {@code @ConfigurationProperties} этот бин автоматически перепривязывается при
 * refresh (Consul KV watch → EnvironmentChangeEvent), поэтому значения таймаутов/URL можно
 * менять из Consul KV на лету. WebClient, который их использует, объявлен {@code @RefreshScope}
 * (см. {@link ConsulConfig}) и пересобирается с новыми значениями без рестарта сервиса.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.services.station-controll")
public class StationClientProperties {

    /** Базовый URL station-controll-service. Прямой DNS-адрес (docker/K8s), НЕ {@code lb://}. */
    private String baseUrl = "http://localhost:8001";

    /** Таймаут установки TCP-соединения, мс. */
    private int connectTimeoutMs = 5000;

    /** Таймаут ожидания ответа, мс. */
    private int readTimeoutMs = 30000;
}
