package charg.ing.stations.config;

import io.netty.channel.ChannelOption;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class ConsulConfig {

    /**
     * Клиент к station-controll-service по прямому DNS-имени
     * ({@code http://station-controll-service:8001}, задаётся в {@link StationClientProperties}).
     *
     * <p>Балансировку берёт на себя инфраструктура (docker DNS сейчас, K8s Service ClusterIP
     * в будущем) — client-side load balancer через Consul (`lb://`) не используется: station-controll
     * работает одним инстансом, а остальные сервисы зовут его так же по DNS.
     *
     * <p>{@code @RefreshScope}: при обновлении конфигурации из Consul KV (watch или
     * {@code POST /actuator/refresh}) бин пересоздаётся, подхватывая новые baseUrl/таймауты из
     * {@link StationClientProperties} — без перезапуска сервиса.
     */
    @Bean
    @RefreshScope
    public WebClient stationControlWebClient(StationClientProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
