package charg.ing.stations.repository;

import charg.ing.stations.entity.TopUp;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface TopUpRepository extends ReactiveCrudRepository<TopUp, UUID> {

    Mono<TopUp> findByOrderId(String orderId);

    Mono<TopUp> findByInvoiceId(String invoiceId);

    /** Top-up history for a user, newest first. */
    Flux<TopUp> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Pending invoices created before the cutoff — used by the reconciliation poller. */
    Flux<TopUp> findByStatusAndInvoiceIdIsNotNullAndCreatedAtBefore(String status, Instant cutoff);
}
