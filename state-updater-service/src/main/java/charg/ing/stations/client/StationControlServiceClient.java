package charg.ing.stations.client;

import charg.ing.stations.config.FeignConfig;
import charg.ing.stations.dto.StationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

//@FeignClient(
//        name = "station-controll-service",
//        url = "${app.services.station-controll-service.url:http://localhost:8001}",
//        configuration = FeignConfig.class
//)

@Component
@RequiredArgsConstructor
public class StationControlServiceClient {

//    @GetMapping("/api/stations")
//    List<StationDTO> getAllStations();


    @Qualifier("plainWebClient")
    private final WebClient webClient;

//    private final WebClient webClient;

//    @Value("${app.services.station-controll-service.url:http://localhost:8001}")
//    private String stationControlServiceUrl;

    @Value("${app.services.station-controll-service.url:http://localhost:8001}")
    private String baseUrl;

    public Flux<StationDTO> getAllStations() {
        return webClient.get()
                .uri(baseUrl + "/api/stations")
                .retrieve()
                .bodyToFlux(StationDTO.class);
    }
}