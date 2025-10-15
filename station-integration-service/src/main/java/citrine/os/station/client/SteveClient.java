package citrine.os.station.client;

import citrine.os.station.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class SteveClient {

    private final WebClient webClient;

    private ExchangeFilterFunction logRequest() {
        return (request, next) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(">>> Request: ").append(request.method()).append(" ").append(request.url()).append("\n");
            request.headers().forEach((name, values) -> values.forEach(value -> sb.append(name).append(": ").append(value).append("\n")));

            // Попробуем извлечь тело, если оно String
            if (request.body() != null) {
                // WebClient не даёт напрямую прочитать body(), поэтому проще логировать его заранее
                sb.append("Body: <cannot log raw body here>");
            }

            System.out.println(sb);
            return next.exchange(request);
        };
    }


    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            System.out.println("<<< Response status code: " + clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }

    public SteveClient(@Value("${steve.base-url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth("admin", "1234"))
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    public Mono<ResponseEntity<RemoteStartResponse>> remoteStart(RemoteStartRequest req) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/remote/start")
                        .queryParam("chargeBoxId", req.getChargeBoxId())
                        .queryParam("connectorId", req.getConnectorId())
                        .queryParam("ocppTag", req.getOcppTag())
                        .build())
                .retrieve()
                .toEntity(RemoteStartResponse.class);
    }

    public Mono<ResponseEntity<RemoteStopResponse>> remoteStop(
            RemoteStopRequest req
    ) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/remote/stop")
                        .queryParam("chargeBoxId", req.getChargeBoxId())
                        .queryParam("connectorId", req.getConnectorId())
                        .queryParam("ocppTag", req.getOcppTag())
                        .build())
                .retrieve()
                .toEntity(RemoteStopResponse.class);
    }

    public Mono<ResponseEntity<String>> getStatus() {
        return webClient.get()
                .uri("/api/v1/connectors")
                .retrieve()
                .toEntity(String.class);
    }
}

