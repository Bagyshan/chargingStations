package charg.ing.stations.repository;

import charg.ing.stations.entity.TransactionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface TransactionRepository extends ReactiveCrudRepository<TransactionEntity, Long> {
    Mono<TransactionEntity> findByTransactionId(Long transactionId);
    Flux<TransactionEntity> findByChargeBoxIdIn(List<String> chargeBoxIds);
}
