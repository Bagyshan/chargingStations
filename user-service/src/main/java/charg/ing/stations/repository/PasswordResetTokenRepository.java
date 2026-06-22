package charg.ing.stations.repository;

import charg.ing.stations.entity.PasswordResetToken;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface PasswordResetTokenRepository extends R2dbcRepository<PasswordResetToken, Long> {

    @Query("SELECT * FROM password_reset_tokens WHERE token = :token AND used = false")
    Mono<PasswordResetToken> findByToken(String token);

    @Query("SELECT * FROM password_reset_tokens WHERE user_id = :userId AND used = false AND expires_at > NOW()")
    Mono<PasswordResetToken> findActiveByUserId(Long userId);

    @Query("UPDATE password_reset_tokens SET used = true WHERE token = :token")
    Mono<Void> markAsUsed(String token);
}