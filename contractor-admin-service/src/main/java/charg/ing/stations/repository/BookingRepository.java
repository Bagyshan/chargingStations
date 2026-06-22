package charg.ing.stations.repository;

import charg.ing.stations.entity.BookingEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface BookingRepository extends ReactiveCrudRepository<BookingEntity, Long> {
    Mono<BookingEntity> findByBookingId(UUID bookingId);
    Flux<BookingEntity> findByStationIdIn(List<String> stationIds);
}
