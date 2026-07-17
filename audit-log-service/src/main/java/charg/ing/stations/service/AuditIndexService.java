package charg.ing.stations.service;

import charg.ing.stations.entity.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ReactiveIndexOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Идемпотентное создание индекса аудита с нужным маппингом (flattened payload,
 * keyword/date поля). Реактивный клиент не создаёт индекс сам на старте, поэтому
 * консьюмер вызывает {@link #ensureIndex()} перед началом приёма событий —
 * иначе первая запись породила бы индекс с динамическим маппингом.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditIndexService {

    private final ReactiveElasticsearchOperations operations;

    public Mono<Void> ensureIndex() {
        ReactiveIndexOperations indexOps = operations.indexOps(AuditEvent.class);
        return indexOps.exists()
                .flatMap(exists -> {
                    if (exists) {
                        log.info("Audit index already exists");
                        return Mono.just(true);
                    }
                    log.info("Audit index not found — creating with mapping");
                    return indexOps.createWithMapping()
                            .doOnNext(created -> log.info("Audit index created: {}", created));
                })
                .then();
    }
}
