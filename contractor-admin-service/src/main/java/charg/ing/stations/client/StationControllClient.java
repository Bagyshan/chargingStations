package charg.ing.stations.client;

import charg.ing.stations.dto.external.AddressDto;
import charg.ing.stations.dto.external.ConnectorTypeDto;
import charg.ing.stations.dto.request.ConnectorPatchRequest;
import charg.ing.stations.dto.request.StationPatchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class StationControllClient {

    private final WebClient webClient;

    public StationControllClient(@Qualifier("stationControllWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Flux<AddressDto> getAllAddresses(String bearerToken) {
        log.debug("Fetching all addresses from station-controll-service");
        return webClient.get()
                .uri("/api/address")
                .header("Authorization", bearerToken)
                .retrieve()
                .bodyToFlux(AddressDto.class)
                .doOnError(e -> log.error("Failed to fetch addresses: {}", e.getMessage()));
    }

    public Flux<ConnectorTypeDto> getAllConnectorTypes(String bearerToken) {
        log.debug("Fetching all connector types from station-controll-service");
        return webClient.get()
                .uri("/api/connector-types")
                .header("Authorization", bearerToken)
                .retrieve()
                .bodyToFlux(ConnectorTypeDto.class)
                .doOnError(e -> log.error("Failed to fetch connector types: {}", e.getMessage()));
    }

    public Mono<Void> patchChargeBox(String chargeBoxId, StationPatchRequest request, String bearerToken) {
        log.debug("Patching chargeBox {} in station-controll-service", chargeBoxId);
        return webClient.patch()
                .uri("/api/stations/{chargeBoxId}", chargeBoxId)
                .header("Authorization", bearerToken)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to patch chargeBox {}: {}", chargeBoxId, e.getMessage()));
    }

    public Mono<Void> patchConnector(String chargeBoxId, Integer connectorId, ConnectorPatchRequest request, String bearerToken) {
        log.debug("Patching connector {}/{} in station-controll-service", chargeBoxId, connectorId);
        return webClient.patch()
                .uri("/api/connectors/{chargeBoxId}/{connectorId}", chargeBoxId, connectorId)
                .header("Authorization", bearerToken)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to patch connector {}/{}: {}", chargeBoxId, connectorId, e.getMessage()));
    }
}
