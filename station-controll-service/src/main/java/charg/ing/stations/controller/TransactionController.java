package charg.ing.stations.controller;

import charg.ing.stations.dto.TransactionHistoryDTO;
import charg.ing.stations.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * История зарядок текущего пользователя.
 *
 * Путь {@code /api/transactions} — аутентифицируемый (в отличие от публичного
 * read-only {@code GET /api/stations/**}), поэтому JWT здесь гарантирован, а userId
 * берётся из {@code sub}. Блокирующий JPA-доступ выносится на boundedElastic, чтобы
 * не блокировать event-loop WebFlux.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public Mono<ResponseEntity<List<TransactionHistoryDTO>>> myTransactions(
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return Mono.fromCallable(() -> transactionService.getUserTransactions(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }
}
