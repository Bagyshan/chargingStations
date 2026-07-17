package charg.ing.stations.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.util.Map;

/**
 * Документ журнала аудита в Elasticsearch.
 *
 * <p>Ключевые решения по индексу:
 * <ul>
 *   <li>{@link #eventId} = {@code _id} документа → повторная доставка из Kafka
 *       перезаписывает тот же документ, дубликатов не будет (идемпотентность).</li>
 *   <li>{@link #payload} — тип {@code flattened}: произвольная структура без «взрыва
 *       маппинга» (одно поле, все листья как keyword, остаётся фильтруемым по
 *       {@code payload.<key>}). Исходные значения/типы сохраняются в {@code _source},
 *       поэтому при чтении payload возвращается как есть.</li>
 *   <li>Топ-левел поля строго типизированы (keyword/date) → быстрые фильтры и сортировка.</li>
 * </ul>
 *
 * {@code createIndex = false}: реактивный клиент не создаёт индекс на старте автоматически —
 * это делает {@code AuditIndexService.ensureIndex()} явно, с нужным маппингом.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "#{@environment.getProperty('audit.elasticsearch.index')}", createIndex = false)
@Setting(shards = 1, replicas = 0)
public class AuditEvent {

    /** Уникальный id события (дедуп) — становится _id документа. */
    @Id
    private String eventId;

    /**
     * Когда событие произошло (в источнике). Список форматов — терпимость к продюсерам:
     * date_time (с миллисекундами — им же сериализуется Instant при записи/фильтре),
     * date_optional_time (ISO без миллисекунд/таймзоны) и epoch_millis.
     */
    @Field(type = FieldType.Date,
            format = {DateFormat.date_time, DateFormat.date_optional_time, DateFormat.epoch_millis})
    private Instant timestamp;

    /** Тип сущности: CHARGE_BOX | CONNECTOR | USER | BALANCE. */
    @Field(type = FieldType.Keyword)
    private String eventType;

    /** Действие: LOGIN, PROFILE_UPDATE, PASSWORD_CHANGE, TOPUP, DEBIT, STATUS_CHANGE, ERROR, ... */
    @Field(type = FieldType.Keyword)
    private String action;

    /** Кто инициировал событие (актор). */
    @Field(type = FieldType.Keyword)
    private String userId;

    /** Сервис-источник события: user-service | payment-service | station-controll-service | ... */
    @Field(type = FieldType.Keyword)
    private String source;

    /** Уровень: INFO | WARN | ERROR (для отдельной выборки ошибок). */
    @Field(type = FieldType.Keyword)
    private String severity;

    /** Id затронутой сущности (chargeBoxId / connectorId / targetUserId / walletId). */
    @Field(type = FieldType.Keyword)
    private String entityId;

    /** Опц. — связать несколько событий одной операции. */
    @Field(type = FieldType.Keyword)
    private String correlationId;

    /** Опц. — IP инициатора (логины и т.п.). */
    @Field(type = FieldType.Keyword)
    private String ip;

    /** Опц. — User-Agent инициатора. */
    @Field(type = FieldType.Text)
    private String userAgent;

    /** Опц. — человекочитаемое описание для полнотекстового поиска. */
    @Field(type = FieldType.Text)
    private String message;

    /** Произвольное наполнение события (структура зависит от типа). */
    @Field(type = FieldType.Flattened)
    private Map<String, Object> payload;

    /** Когда событие принято и сохранено сервисом аудита. */
    @Field(type = FieldType.Date,
            format = {DateFormat.date_time, DateFormat.date_optional_time, DateFormat.epoch_millis})
    private Instant receivedAt;
}
