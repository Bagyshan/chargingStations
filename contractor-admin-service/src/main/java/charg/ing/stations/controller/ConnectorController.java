package charg.ing.stations.controller;

import charg.ing.stations.dto.request.ConnectorRequest;
import charg.ing.stations.dto.response.ConnectorResponse;
import charg.ing.stations.service.ConnectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
@Tag(name = "Connectors", description = "CRUD operations for charge box connectors")
public class ConnectorController {

    private final ConnectorService connectorService;

    @GetMapping
    @Operation(summary = "Get all connectors (CONTRACTOR sees only connectors of their stations)")
    public Flux<ConnectorResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return connectorService.getAll(jwt);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get connector by database id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connector found"),
            @ApiResponse(responseCode = "404", description = "Connector not found")
    })
    public Mono<ResponseEntity<ConnectorResponse>> getById(
            @Parameter(description = "Database primary key") @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {
        return connectorService.getById(id, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-charge-box/{chargeBoxId}")
    @Operation(summary = "Get all connectors for a charge box")
    public Flux<ConnectorResponse> getByChargeBoxId(
            @Parameter(description = "OCPP charge box identifier", example = "CP_001")
            @PathVariable String chargeBoxId,
            @AuthenticationPrincipal Jwt jwt) {
        return connectorService.getByChargeBoxId(chargeBoxId, jwt);
    }

    @GetMapping("/by-charge-box/{chargeBoxId}/{connectorId}")
    @Operation(summary = "Get a specific connector by chargeBoxId and connectorId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connector found"),
            @ApiResponse(responseCode = "404", description = "Connector not found")
    })
    public Mono<ResponseEntity<ConnectorResponse>> getByChargeBoxIdAndConnectorId(
            @Parameter(description = "OCPP charge box identifier", example = "CP_001")
            @PathVariable String chargeBoxId,
            @Parameter(description = "Connector number", example = "1")
            @PathVariable Integer connectorId,
            @AuthenticationPrincipal Jwt jwt) {
        return connectorService.getByChargeBoxIdAndConnectorId(chargeBoxId, connectorId, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new connector (CONTRACTOR must own the target charge box)")
    @ApiResponse(responseCode = "201", description = "Connector created")
    public Mono<ConnectorResponse> create(
            @RequestBody ConnectorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return connectorService.create(request, jwt);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a connector (only provided fields are updated)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connector updated"),
            @ApiResponse(responseCode = "404", description = "Connector not found")
    })
    public Mono<ResponseEntity<ConnectorResponse>> update(
            @Parameter(description = "Database primary key") @PathVariable Integer id,
            @RequestBody ConnectorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return connectorService.update(id, request, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a connector")
    @ApiResponse(responseCode = "204", description = "Connector deleted")
    public Mono<Void> delete(
            @Parameter(description = "Database primary key") @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {
        return connectorService.delete(id, jwt);
    }
}
