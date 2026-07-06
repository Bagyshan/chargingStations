package charg.ing.stations.repository;

import charg.ing.stations.entity.DeviceToken;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DeviceTokenRepository extends R2dbcRepository<DeviceToken, Long> {

    Flux<DeviceToken> findByKeycloakId(String keycloakId);

    /**
     * Upsert по токену: устройство одно, а пользователь на нём мог смениться —
     * тогда токен перепривязывается к новому keycloakId.
     */
    @Modifying
    @Query("""
            INSERT INTO device_tokens (keycloak_id, token, platform, updated_at)
            VALUES (:keycloakId, :token, :platform, CURRENT_TIMESTAMP)
            ON CONFLICT (token) DO UPDATE
            SET keycloak_id = EXCLUDED.keycloak_id,
                platform = EXCLUDED.platform,
                updated_at = CURRENT_TIMESTAMP
            """)
    Mono<Integer> upsert(String keycloakId, String token, String platform);

    Mono<Void> deleteByToken(String token);

    Mono<Void> deleteByKeycloakIdAndToken(String keycloakId, String token);
}
