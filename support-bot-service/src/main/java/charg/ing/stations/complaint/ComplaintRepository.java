package charg.ing.stations.complaint;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ComplaintRepository extends R2dbcRepository<ComplaintEntity, Long> {

    /** Все обращения, новые — первыми (для будущей админки). */
    Flux<ComplaintEntity> findAllByOrderByCreatedAtDesc();
}
