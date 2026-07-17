package charg.ing.stations.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Бизнес-таймауты станций, вынесенные в конфиг.
 *
 * <p>Как {@code @ConfigurationProperties}-бин он автоматически перепривязывается при refresh
 * (Consul KV watch → EnvironmentChangeEvent → ConfigurationPropertiesRebinder), поэтому значения
 * можно менять из Consul KV на лету. Потребители обязаны читать геттеры В МОМЕНТ использования
 * (не кэшировать в поле), тогда новое значение применится без рестарта — см.
 * {@link charg.ing.stations.service.StationConnectivityService#sweepOffline()}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "station")
public class StationTimeoutProperties {

    /**
     * Окно тишины offline-свипа, сек. Должно превышать интервал OCPP-heartbeat станций
     * (дефолт SteVe — 14400 c), поэтому по умолчанию 6 часов. Ключ: {@code station.offline-threshold-seconds}.
     */
    private long offlineThresholdSeconds = 21600;
}
