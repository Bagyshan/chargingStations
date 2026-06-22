package charg.ing.stations.repository;

import charg.ing.stations.entity.User;
import charg.ing.stations.entity.enums.UserRole;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {

    Mono<User> findByEmail(String email);

    Mono<User> findByKeycloakId(String keycloakId);

    Mono<User> findByPhone(String phone);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByPhone(String phone);

    @Query("SELECT * FROM users WHERE email = :email AND active = true")
    Mono<User> findActiveByEmail(String email);

//    @Query("SELECT * FROM users WHERE role = :role AND active = true")
//    Flux<User> findByRole(UserRole role);

    @Query("SELECT * FROM users WHERE email ILIKE :pattern")
    Flux<User> findByEmailContaining(String pattern);

    @Query("SELECT COUNT(*) FROM users WHERE active = :active")
    Mono<Long> countByActive(boolean active);

    @Query("SELECT COUNT(*) FROM users WHERE email_verified = :verified")
    Mono<Long> countByEmailVerified(boolean verified);

    @Query("SELECT COUNT(*) FROM users WHERE role = :role")
    Mono<Long> countByRole(UserRole role);

    @Query("SELECT * FROM users WHERE role = :role ORDER BY created_at DESC")
    Flux<User> findByRole(UserRole role);

    @Query("SELECT * FROM users WHERE active = :active ORDER BY created_at DESC")
    Flux<User> findByActive(boolean active);

    @Query("SELECT * FROM users WHERE created_at >= :fromDate AND created_at <= :toDate")
    Flux<User> findByCreatedAtBetween(LocalDateTime fromDate, LocalDateTime toDate);
}