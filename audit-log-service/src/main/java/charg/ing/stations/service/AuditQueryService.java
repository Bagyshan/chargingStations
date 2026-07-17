package charg.ing.stations.service;

import charg.ing.stations.dto.AuditSearchCriteria;
import charg.ing.stations.dto.PageResponse;
import charg.ing.stations.entity.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Поиск по журналу аудита через реактивный {@link ReactiveElasticsearchOperations}.
 * Динамически собирает {@link CriteriaQuery} из переданных фильтров, сортирует по
 * {@code timestamp desc} и возвращает страницу с точным total.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final ReactiveElasticsearchOperations operations;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    public Mono<PageResponse<AuditEvent>> search(AuditSearchCriteria c) {
        int page = Math.max(c.page(), 0);
        int size = c.size() <= 0 ? DEFAULT_SIZE : Math.min(c.size(), MAX_SIZE);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        CriteriaQuery query = new CriteriaQuery(buildCriteria(c), pageable);
        query.setTrackTotalHits(true); // точный total даже при >10k совпадений

        return operations.searchForHits(query, AuditEvent.class)
                .flatMap(hits -> hits.getSearchHits()
                        .map(SearchHit::getContent)
                        .collectList()
                        .map(content -> {
                            long total = hits.getTotalHits();
                            int totalPages = (int) Math.ceil((double) total / size);
                            return new PageResponse<>(content, page, size, total, totalPages);
                        }));
    }

    public Mono<AuditEvent> findById(String eventId) {
        return operations.get(eventId, AuditEvent.class);
    }

    private Criteria buildCriteria(AuditSearchCriteria c) {
        Criteria criteria = new Criteria(); // пустой = match_all, дальше уточняем через and()

        if (StringUtils.hasText(c.eventType())) {
            criteria = criteria.and(Criteria.where("eventType").is(c.eventType()));
        }
        if (StringUtils.hasText(c.action())) {
            criteria = criteria.and(Criteria.where("action").is(c.action()));
        }
        if (StringUtils.hasText(c.userId())) {
            criteria = criteria.and(Criteria.where("userId").is(c.userId()));
        }
        if (StringUtils.hasText(c.entityId())) {
            criteria = criteria.and(Criteria.where("entityId").is(c.entityId()));
        }
        if (StringUtils.hasText(c.source())) {
            criteria = criteria.and(Criteria.where("source").is(c.source()));
        }
        if (StringUtils.hasText(c.severity())) {
            criteria = criteria.and(Criteria.where("severity").is(c.severity()));
        }
        if (StringUtils.hasText(c.correlationId())) {
            criteria = criteria.and(Criteria.where("correlationId").is(c.correlationId()));
        }

        // Период по timestamp (любая из границ опциональна).
        if (c.from() != null && c.to() != null) {
            criteria = criteria.and(Criteria.where("timestamp").between(c.from(), c.to()));
        } else if (c.from() != null) {
            criteria = criteria.and(Criteria.where("timestamp").greaterThanEqual(c.from()));
        } else if (c.to() != null) {
            criteria = criteria.and(Criteria.where("timestamp").lessThanEqual(c.to()));
        }

        // Полнотекст по человекочитаемому message.
        if (StringUtils.hasText(c.q())) {
            criteria = criteria.and(Criteria.where("message").matches(c.q()));
        }

        // Точечный фильтр по подполю flattened-payload (напр. payloadKey=provider, payloadValue=ODENGI).
        if (StringUtils.hasText(c.payloadKey()) && StringUtils.hasText(c.payloadValue())) {
            criteria = criteria.and(Criteria.where("payload." + c.payloadKey()).is(c.payloadValue()));
        }

        return criteria;
    }
}
