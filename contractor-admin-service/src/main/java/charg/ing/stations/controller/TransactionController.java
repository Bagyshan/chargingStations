package charg.ing.stations.controller;

import charg.ing.stations.dto.response.TransactionResponse;
import charg.ing.stations.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Read-only access to charging transactions")
public class TransactionController {

    private final TransactionQueryService transactionQueryService;

    @GetMapping
    @Operation(summary = "Get all transactions (CONTRACTOR sees only transactions of their stations)")
    public Flux<TransactionResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return transactionQueryService.getAll(jwt);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by database id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public Mono<ResponseEntity<TransactionResponse>> getById(
            @Parameter(description = "Database primary key") @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return transactionQueryService.getById(id, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-transaction-id/{transactionId}")
    @Operation(summary = "Get transaction by OCPP transaction id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public Mono<ResponseEntity<TransactionResponse>> getByTransactionId(
            @Parameter(description = "OCPP transaction identifier") @PathVariable Long transactionId,
            @AuthenticationPrincipal Jwt jwt) {
        return transactionQueryService.getByTransactionId(transactionId, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
