package charg.ing.stations.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${services.station-controll.url}")
    private String stationControllUrl;

    @Value("${services.user-service.url}")
    private String userServiceUrl;

    @Bean("stationControllWebClient")
    public WebClient stationControllWebClient() {
        return WebClient.builder()
                .baseUrl(stationControllUrl)
                .build();
    }

    @Bean("userServiceWebClient")
    public WebClient userServiceWebClient() {
        return WebClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }
}
