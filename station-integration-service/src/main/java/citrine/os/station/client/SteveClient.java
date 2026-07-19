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
            request.headers().forEach((name, values) -> values.forEach(value ->
                    sb.append(name).append(": ")
                      .append("authorization".equalsIgnoreCase(name) ? "<redacted>" : value)
                      .append("\n")));

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

    public SteveClient(
            @Value("${steve.base-url}") String baseUrl,
            @Value("${steve.username:admin}") String username,
            @Value("${steve.api-password:}") String apiPassword) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                // Креды берутся из конфига (env STEVE_AUTH_PASSWORD) — единый API-пароль SteVe.
                .defaultHeaders(headers -> headers.setBasicAuth(username, apiPassword))
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
                // .path() дописывает к base-path (учитывает context-path /steve);
                // строковый "/api/..." затёр бы префикс /steve.
                .uri(uriBuilder -> uriBuilder.path("/api/v1/connectors").build())
                .retrieve()
                .toEntity(String.class);
    }
}

