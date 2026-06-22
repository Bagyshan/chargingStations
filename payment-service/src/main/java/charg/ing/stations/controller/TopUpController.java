package charg.ing.stations.controller;

import charg.ing.stations.dto.TopUpInitiateRequest;
import charg.ing.stations.dto.TopUpResponse;
import charg.ing.stations.entity.TopUpStatus;
import charg.ing.stations.service.TopUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/balance")
@Tag(name = "top-up", description = "Пополнение кошелька через O!Dengi (QR / банки)")
public class TopUpController {

    private final TopUpService topUpService;

    public TopUpController(TopUpService topUpService) {
        this.topUpService = topUpService;
    }

    @Operation(summary = "Создать счёт на пополнение и получить QR / deeplink / paylink")
    @PostMapping(value = "/{userId}/top-up/initiate",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TopUpResponse>> initiate(@PathVariable UUID userId,
                                                        @Valid @RequestBody TopUpInitiateRequest request) {
        return topUpService.initiate(userId, request.getAmount(), request.getDescription())
                .map(t -> ResponseEntity.status(HttpStatus.CREATED).body(TopUpResponse.from(t)));
    }

    @Operation(summary = "История пополнений пользователя")
    @GetMapping(value = "/{userId}/top-up/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<TopUpResponse> history(@PathVariable UUID userId) {
        return topUpService.history(userId).map(TopUpResponse::from);
    }

    @Operation(summary = "Статус пополнения (с принудительной сверкой, если ещё в ожидании)")
    @GetMapping(value = "/top-up/{orderId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TopUpResponse>> status(@PathVariable String orderId) {
        return topUpService.getByOrderId(orderId)
                .flatMap(t -> {
                    boolean pending = TopUpStatus.PENDING.name().equals(t.getStatus()) && t.getInvoiceId() != null;
                    Mono<Void> maybeReconcile = pending ? topUpService.reconcile(t) : Mono.empty();
                    return maybeReconcile.then(topUpService.getByOrderId(orderId));
                })
                .map(t -> ResponseEntity.ok(TopUpResponse.from(t)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
