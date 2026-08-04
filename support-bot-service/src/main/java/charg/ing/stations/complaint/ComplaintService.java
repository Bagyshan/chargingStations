package charg.ing.stations.complaint;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository repository;

    /** Сохранить обращение. id (BIGSERIAL) проставляется базой. */
    public Mono<ComplaintEntity> save(ComplaintEntity complaint) {
        return repository.save(complaint);
    }
}
