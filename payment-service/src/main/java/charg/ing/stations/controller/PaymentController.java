package charg.ing.stations.controller;


import charg.ing.stations.dto.BalanceDto;
import charg.ing.stations.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/balance")
@Tag(name = "payment", description = "Баланс пользователей")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) { this.service = service; }

    @Operation(summary = "Получить баланс пользователя")
    @GetMapping(value = "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BalanceDto>> getBalance(@PathVariable UUID userId) {
        return service.getBalance(userId)
                .map(ub -> ResponseEntity.ok(new BalanceDto(ub.getUserId(), ub.getBalance(), ub.isBooking())));
    }
}
