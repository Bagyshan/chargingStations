package charg.ing.stations.controller;

import charg.ing.stations.dto.connector_type.ConnectorTypeResponse;
import charg.ing.stations.service.ConnectorTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/connector-types")
@Slf4j
public class ConnectorTypeController {

    private final ConnectorTypeService connectorTypeService;

    public ConnectorTypeController(ConnectorTypeService connectorTypeService) {
        this.connectorTypeService = connectorTypeService;
    }

    @GetMapping
    public Flux<ConnectorTypeResponse> getAll(ServerWebExchange exchange) {
        String baseUrl = getBaseUrl(exchange);
        return connectorTypeService.getAllConnectorTypes()
                .map(response -> enrichWithBaseUrl(response, baseUrl));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ConnectorTypeResponse>> getById(
            @PathVariable Integer id, ServerWebExchange exchange) {
        String baseUrl = getBaseUrl(exchange);
        return connectorTypeService.getConnectorTypeById(id)
                .map(response -> enrichWithBaseUrl(response, baseUrl))
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ConnectorTypeResponse>> create(
            @RequestPart("connectorTypeName") String connectorTypeName,
            @RequestPart(value = "icon", required = false) Mono<FilePart> iconMono) {
        return iconMono
                .flatMap(filePart -> connectorTypeService.createConnectorType(connectorTypeName, filePart))
                .switchIfEmpty(connectorTypeService.createConnectorType(connectorTypeName, null))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ConnectorTypeResponse>> update(
            @PathVariable Integer id,
            @RequestPart("connectorTypeName") String connectorTypeName,
            @RequestPart(value = "icon", required = false) Mono<FilePart> iconMono) {
        return iconMono
                .flatMap(filePart -> connectorTypeService.updateConnectorType(id, connectorTypeName, filePart))
                .switchIfEmpty(connectorTypeService.updateConnectorType(id, connectorTypeName, null))
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Object>> delete(@PathVariable Integer id) {
        return connectorTypeService.deleteConnectorType(id)
                .then(Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of("error", e.getMessage()))));
    }

    private String getBaseUrl(ServerWebExchange exchange) {
        return exchange.getRequest().getURI().getScheme() + "://" +
                exchange.getRequest().getURI().getHost() + ":" +
                exchange.getRequest().getURI().getPort();
    }

    private ConnectorTypeResponse enrichWithBaseUrl(ConnectorTypeResponse response, String baseUrl) {
        if (response.getConnectorTypeIcon() != null && !response.getConnectorTypeIcon().startsWith("http")) {
            response.setConnectorTypeIcon(baseUrl + response.getConnectorTypeIcon());
        }
        return response;
    }
}