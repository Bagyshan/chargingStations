package charg.ing.stations.repository;

import charg.ing.stations.entity.AuditEvent;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Реактивный доступ к индексу аудита. {@code save} с уже существующим {@code eventId}
 * перезаписывает документ — на этом и построена дедупликация.
 */
@Repository
public interface AuditEventRepository extends ReactiveElasticsearchRepository<AuditEvent, String> {
}
