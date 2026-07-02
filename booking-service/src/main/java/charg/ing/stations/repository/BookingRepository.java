package charg.ing.stations.repository;

import charg.ing.stations.entity.BookingEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface BookingRepository extends R2dbcRepository<BookingEntity, UUID> {

    Flux<BookingEntity> findByStatus(String status);

    Mono<BookingEntity> findFirstByUserIdAndStatusOrderByStartedAtDesc(UUID userId, String status);

    Flux<BookingEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

}