package charg.ing.stations.client;

import charg.ing.stations.dto.StationDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Реактивный клиент к station-controll-service.
 *
 * <p>Зовём сервис ПО ИМЕНИ через Consul discovery: {@code lb://station-controll-service}
 * (baseUrl зашит в {@code stationControlWebClient}, см. {@link charg.ing.stations.config.ConsulConfig}).
 * Никаких хардкод-хостов/портов — инстанс выбирает load balancer из реестра.
 *
 * <p>Feign в реактивном стеке не поддерживается — используется {@link WebClient}.
 */
@Component
public class StationControlServiceClient {

    private final WebClient webClient;

    public StationControlServiceClient(WebClient stationControlWebClient) {
        this.webClient = stationControlWebClient;
    }

    public Flux<StationDTO> getAllStations() {
        return webClient.get()
                .uri("/api/stations")
                .retrieve()
                .bodyToFlux(StationDTO.class);
    }
}
