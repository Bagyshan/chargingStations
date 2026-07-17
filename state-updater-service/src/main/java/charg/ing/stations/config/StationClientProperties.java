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

    /** Базовый URL. {@code lb://<service-name>} — балансировка по инстансам из Consul discovery. */
    private String baseUrl = "lb://station-controll-service";

    /** Таймаут установки TCP-соединения, мс. */
    private int connectTimeoutMs = 5000;

    /** Таймаут ожидания ответа, мс. */
    private int readTimeoutMs = 30000;
}
