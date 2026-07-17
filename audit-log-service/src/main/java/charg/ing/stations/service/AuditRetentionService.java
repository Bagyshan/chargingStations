package charg.ing.stations.service;

import charg.ing.stations.entity.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Retention журнала аудита: по расписанию удаляет события старше {@code audit.retention.days}
 * через delete-by-query по {@code timestamp}.
 *
 * <p>Почему delete-by-query, а не ILM-rollover: rollover раскидывает документы по нескольким
 * индексам, и дедуп по {@code _id} (наша гарантия против дублей повторной доставки) перестаёт
 * работать между индексами. Один индекс + периодическая чистка сохраняет идемпотентность и даёт
 * retention. Для очень больших объёмов при желании можно перейти на data stream + ILM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRetentionService {

    private final ReactiveElasticsearchOperations operations;

    /** Сколько хранить события. {@code <= 0} — retention выключен. */
    @Value("${audit.retention.days:180}")
    private long retentionDays;

    @Scheduled(cron = "${audit.retention.cron:0 0 3 * * *}")
    public void purgeExpired() {
        if (retentionDays <= 0) {
            log.debug("Audit retention disabled (audit.retention.days={})", retentionDays);
            return;
        }
        Instant threshold = Instant.now().minus(Duration.ofDays(retentionDays));
        CriteriaQuery query = new CriteriaQuery(Criteria.where("timestamp").lessThanEqual(threshold));
        operations.delete(query, AuditEvent.class)
                .subscribe(
                        resp -> log.info("Audit retention: deleted {} event(s) older than {} ({} days)",
                                resp.getDeleted(), threshold, retentionDays),
                        err -> log.error("Audit retention purge failed", err));
    }
}
