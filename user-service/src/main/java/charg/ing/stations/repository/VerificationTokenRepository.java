package charg.ing.stations.repository;

import charg.ing.stations.entity.VerificationToken;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface VerificationTokenRepository extends R2dbcRepository<VerificationToken, Long> {

    @Query("SELECT * FROM verification_tokens WHERE token = :token AND used = false")
    Mono<VerificationToken> findByToken(String token);

    /** Все токены заданного типа, новые — первыми (для админ-обзора OTP). */
    @Query("SELECT * FROM verification_tokens WHERE token_type = :tokenType ORDER BY created_at DESC")
    Flux<VerificationToken> findAllByTokenTypeOrderByCreatedAtDesc(VerificationToken.TokenType tokenType);

    @Query("SELECT * FROM verification_tokens WHERE user_id = :userId AND token_type = :tokenType AND used = false AND expires_at > NOW() LIMIT 1")
    Mono<VerificationToken> findActiveByUserIdAndType(Long userId, VerificationToken.TokenType tokenType);

    @Query("UPDATE verification_tokens SET used = true WHERE token = :token")
    Mono<Void> markAsUsed(String token);

    /** Удалить все verification-токены пользователя (удаление аккаунта). */
    Mono<Void> deleteByUserId(Long userId);
}