package charg.ing.stations.repository;

import charg.ing.stations.entity.UserBalance;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserBalanceRepository extends ReactiveCrudRepository<UserBalance, UUID> {
    // CRUD provided by ReactiveCrudRepository
    Mono<UserBalance> findByUserId(UUID userId);
}