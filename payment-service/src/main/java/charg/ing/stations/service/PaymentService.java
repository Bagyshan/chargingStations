package charg.ing.stations.service;


import charg.ing.stations.entity.UserBalance;
import charg.ing.stations.repository.UserBalanceRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {

    private final UserBalanceRepository repository;
    private final DatabaseClient databaseClient;
    private final R2dbcEntityTemplate entityTemplate;


    public Mono<UserBalance> getBalance(UUID userId) {
        return repository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    // Auto-create record with zero balance.
                    // entityTemplate.insert (а не repository.save): у сущности задан @Id,
                    // поэтому R2DBC трактовал бы save() как UPDATE и падал бы на несуществующей строке.
                    UserBalance ub = new UserBalance(userId, BigDecimal.ZERO.setScale(2), false);
                    return entityTemplate.insert(ub);
                }));
    }

    /**
     * Топ-ап: атомарно увеличиваем баланс.
     */
    @Transactional
    public Mono<UserBalance> topUp(UUID userId, BigDecimal amount) {
        amount = amount.setScale(2);
        // Найдем и обновим. Используем repository последовательность (find -> save).
        BigDecimal finalAmount = amount;
        return repository.findById(userId)
                .flatMap(existing -> {
                    BigDecimal newBal = existing.getBalance().add(finalAmount);
                    existing.setBalance(newBal);
                    return repository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    UserBalance ub = new UserBalance(userId, finalAmount, false);
                    return entityTemplate.insert(ub);
                }));
    }

    public Mono<UserBalance> createOrUpdate(UUID userId) {

        UserBalance ub = new UserBalance(userId, BigDecimal.ZERO.setScale(2), false);

        return repository.save(ub);
    }

    public Mono<UserBalance> createEmptyWallet(UUID userId) {
        return repository.findById(userId)
                .switchIfEmpty(
                        entityTemplate.insert(
                                new UserBalance(
                                        userId,
                                        BigDecimal.ZERO.setScale(2),
                                        false
                                )
                        )
                );
    }
}