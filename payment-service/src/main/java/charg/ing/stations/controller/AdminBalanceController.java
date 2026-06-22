package charg.ing.stations.controller;

import charg.ing.stations.dto.BalanceDto;
import charg.ing.stations.dto.TopUpRequest;
import charg.ing.stations.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * Administrative balance operations — guarded by ROLE_ADMIN in {@link charg.ing.stations.config.SecurityConfig}.
 * These bypass the O!Dengi payment flow (manual credit / wallet provisioning) and must never be public.
 */
@RestController
@RequestMapping("/api/v1/admin/balance")
@Tag(name = "admin-balance", description = "Админские операции с балансом (только ROLE_ADMIN)")
public class AdminBalanceController {

    private final PaymentService service;

    public AdminBalanceController(PaymentService service) {
        this.service = service;
    }

    @Operation(summary = "[ADMIN] Ручное пополнение баланса (без оплаты)")
    @PostMapping(value = "/{userId}/top-up", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BalanceDto>> topUp(@PathVariable UUID userId,
                                                  @Valid @RequestBody TopUpRequest request) {
        return service.topUp(userId, request.getAmount())
                .map(ub -> ResponseEntity
                        .created(URI.create("/api/v1/balance/" + ub.getUserId()))
                        .body(new BalanceDto(ub.getUserId(), ub.getBalance(), ub.isBooking())));
    }

    @Operation(summary = "[ADMIN] Создать пустой кошелёк для пользователя")
    @PostMapping(value = "/{userId}/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BalanceDto>> createEmptyWallet(@PathVariable UUID userId) {
        return service.createEmptyWallet(userId)
                .map(ub -> ResponseEntity
                        .created(URI.create("/api/v1/balance/" + ub.getUserId()))
                        .body(new BalanceDto(ub.getUserId(), ub.getBalance(), ub.isBooking())));
    }
}
