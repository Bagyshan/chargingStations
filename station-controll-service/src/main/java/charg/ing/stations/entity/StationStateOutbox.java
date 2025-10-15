package charg.ing.stations.entity;

import charg.ing.stations.util.JsonbConverter;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "station_state_outbox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationStateOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "published")
    private boolean published;

    @Column(name = "published_at")
    private Instant publishedAt;
}