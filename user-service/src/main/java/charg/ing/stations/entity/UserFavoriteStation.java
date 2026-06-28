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
 * Избранная станция пользователя. Хранит только membership (кто какую станцию отметил) —
 * источник истины для избранного. Сами данные станции (расстояние, статусы, тариф) НЕ дублируются:
 * они подтягиваются клиентом из кэша state-updater-service по списку chargeBoxId.
 *
 * <p>Ключуется по {@code keycloakId} (UUID из JWT {@code sub}) — тем же id, что и кошелёк/сага оплаты.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_favorite_stations")
public class UserFavoriteStation {

    @Id
    private Long id;

    @Column("keycloak_id")
    private String keycloakId;

    @Column("charge_box_id")
    private String chargeBoxId;

    @Column("created_at")
    private LocalDateTime createdAt;
}
