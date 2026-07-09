package charg.ing.stations.controller;

import charg.ing.stations.dto.connector_type.ConnectorTypeResponse;
import charg.ing.stations.service.ConnectorTypeService;
import charg.ing.stations.util.IconUrlResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/connector-types")
@Slf4j
public class ConnectorTypeController {

    private final ConnectorTypeService connectorTypeService;
    private final IconUrlResolver iconUrlResolver;

    public ConnectorTypeController(ConnectorTypeService connectorTypeService,
                                   IconUrlResolver iconUrlResolver) {
        this.connectorTypeService = connectorTypeService;
        this.iconUrlResolver = iconUrlResolver;
    }

    @GetMapping
    public Flux<ConnectorTypeResponse> getAll() {
        return connectorTypeService.getAllConnectorTypes()
                .map(this::enrichIcon);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ConnectorTypeResponse>> getById(@PathVariable Integer id) {
        return connectorTypeService.getConnectorTypeById(id)
                .map(this::enrichIcon)
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

    /**
     * Приводит иконку к публичному абсолютному URL (единый источник — конфиг
     * {@code app.public-base-url}), а не к хосту входящего запроса, который через
     * gateway/WebClient давал внутренний {@code station-controll-service:8001}.
     */
    private ConnectorTypeResponse enrichIcon(ConnectorTypeResponse response) {
        response.setConnectorTypeIcon(iconUrlResolver.resolve(response.getConnectorTypeIcon()));
        return response;
    }
}