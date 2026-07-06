package charg.ing.stations.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * FCM-токен устройства пользователя для push-уведомлений. Одно устройство — одна
 * строка (токен уникален); токен может «переехать» к другому пользователю, если на
 * том же устройстве залогинились под другим аккаунтом (upsert по token).
 *
 * <p>Ключуется по {@code keycloakId} (UUID из JWT {@code sub}) — тем же id, что и
 * события {@code charging.user.status} / {@code booking.state} / {@code payment.*},
 * поэтому notification-service находит адресата пуша без дополнительных маппингов.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("device_tokens")
public class DeviceToken {

    @Id
    private Long id;

    @Column("keycloak_id")
    private String keycloakId;

    @Column("token")
    private String token;

    @Column("platform")
    private String platform;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
