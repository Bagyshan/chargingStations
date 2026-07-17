package charg.ing.stations.config;

import io.netty.channel.ChannelOption;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
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
     * LoadBalanced билдер: WebClient, построенный из него, умеет резолвить {@code lb://<service>}
     * в живой инстанс через Consul discovery (reactive load balancer).
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Клиент к station-controll-service «по имени» ({@code lb://station-controll-service}).
     *
     * <p>{@code @RefreshScope}: при обновлении конфигурации из Consul KV (watch или
     * {@code POST /actuator/refresh}) бин пересоздаётся, подхватывая новые baseUrl/таймауты из
     * {@link StationClientProperties} — без перезапуска сервиса.
     */
    @Bean
    @RefreshScope
    public WebClient stationControlWebClient(@LoadBalanced WebClient.Builder loadBalancedBuilder,
                                             StationClientProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return loadBalancedBuilder.clone()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
