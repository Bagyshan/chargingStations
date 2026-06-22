package charg.ing.stations.controller;

import charg.ing.stations.dto.request.ChargeBoxRequest;
import charg.ing.stations.dto.response.ChargeBoxResponse;
import charg.ing.stations.service.ChargeBoxService;
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
@RequestMapping("/api/charge-boxes")
@RequiredArgsConstructor
@Tag(name = "Charge Boxes", description = "CRUD operations for charging station charge boxes")
public class ChargeBoxController {

    private final ChargeBoxService chargeBoxService;

    @GetMapping
    @Operation(summary = "Get all charge boxes (CONTRACTOR sees only their own)")
    public Flux<ChargeBoxResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return chargeBoxService.getAll(jwt);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get charge box by database id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Charge box found"),
            @ApiResponse(responseCode = "404", description = "Charge box not found")
    })
    public Mono<ResponseEntity<ChargeBoxResponse>> getById(
            @Parameter(description = "Database primary key") @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {
        return chargeBoxService.getById(id, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-charge-box-id/{chargeBoxId}")
    @Operation(summary = "Get charge box by OCPP chargeBoxId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Charge box found"),
            @ApiResponse(responseCode = "404", description = "Charge box not found")
    })
    public Mono<ResponseEntity<ChargeBoxResponse>> getByChargeBoxId(
            @Parameter(description = "OCPP charge box identifier", example = "CP_001")
            @PathVariable String chargeBoxId,
            @AuthenticationPrincipal Jwt jwt) {
        return chargeBoxService.getByChargeBoxId(chargeBoxId, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new charge box (CONTRACTOR auto-assigns ownerId from token)")
    @ApiResponse(responseCode = "201", description = "Charge box created")
    public Mono<ChargeBoxResponse> create(
            @RequestBody ChargeBoxRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return chargeBoxService.create(request, jwt);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a charge box (only provided fields are updated)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Charge box updated"),
            @ApiResponse(responseCode = "404", description = "Charge box not found")
    })
    public Mono<ResponseEntity<ChargeBoxResponse>> update(
            @Parameter(description = "Database primary key") @PathVariable Integer id,
            @RequestBody ChargeBoxRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return chargeBoxService.update(id, request, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a charge box")
    @ApiResponse(responseCode = "204", description = "Charge box deleted")
    public Mono<Void> delete(
            @Parameter(description = "Database primary key") @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {
        return chargeBoxService.delete(id, jwt);
    }
}
