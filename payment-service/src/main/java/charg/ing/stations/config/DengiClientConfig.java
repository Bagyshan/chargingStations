package charg.ing.stations.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(DengiProperties.class)
public class DengiClientConfig {

    /** Dedicated WebClient for the O!Dengi JSON API with sane connect/response timeouts. */
    @Bean("dengiWebClient")
    public WebClient dengiWebClient(DengiProperties props) {
        HttpClient httpClient = HttpClient.create()
                // Явный connect-timeout (по умолчанию Netty ждёт 30с) — при недоступной песочнице
                // коннект падает быстро и reconcile-scheduler не висит, а до-сверяет на след. цикле.
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(20));
        return WebClient.builder()
                .baseUrl(props.getApiUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
