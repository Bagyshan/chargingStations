package charg.ing.stations.kafka;

import charg.ing.stations.audit.AuditEventPublisher;
import charg.ing.stations.dto.event.TransactionEventMessage;
import charg.ing.stations.entity.UserBalance;
import charg.ing.stations.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Settles charging against the prepaid wallet, mirroring the booking flow:
 * <ul>
 *   <li>START_TRANSACTION (no stop timestamp) → set {@code is_charging = true};</li>
 *   <li>STOP_TRANSACTION → debit {@code totalSum} (consumed kWh × price) and clear the flag.</li>
 * </ul>
 * The {@code is_charging} guard makes the debit safe against duplicate stop events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventsConsumer {

    private final UserBalanceRepository balanceRepository;
    private final AuditEventPublisher auditPublisher;

    @KafkaListener(topics = "transaction.events", groupId = "payment-service-charging-group")
    public void handleTransactionEvent(TransactionEventMessage event) {
        log.info("Received transaction event: {}", event);

        if (event.getUserId() == null) {
            log.warn("Transaction event without userId, ignoring: {}", event);
            return;
        }

        final UUID userId;
        try {
            userId = UUID.fromString(event.getUserId());
        } catch (IllegalArgumentException e) {
            log.warn("Transaction event with non-UUID userId '{}', ignoring", event.getUserId());
            return;
        }

        boolean isStop = event.getStopTimestamp() != null
                || "COMPLETED".equals(event.getStatus())
                || "CANCELLED".equals(event.getStatus());

        Mono<UserBalance> action = isStop
                ? settleStop(userId, event.getTotalSum())
                : flagCharging(userId);

        action.subscribe(
                saved -> log.info("Charging settlement applied for user {}: balance={}, isCharging={}",
                        userId, saved.getBalance(), saved.isCharging()),
                err -> log.error("Error applying transaction event for user {}: {}", userId, err.getMessage()));
    }

    private Mono<UserBalance> flagCharging(UUID userId) {
        return balanceRepository.findByUserId(userId)
                .flatMap(balance -> {
                    if (balance.isCharging()) {
                        return Mono.just(balance);
                    }
                    balance.setCharging(true);
                    return balanceRepository.save(balance);
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.error("Balance not found for user {} on charging start", userId)));
    }

    private Mono<UserBalance> settleStop(UUID userId, BigDecimal totalSum) {
        return balanceRepository.findByUserId(userId)
                .flatMap(balance -> {
                    if (!balance.isCharging()) {
                        // Already settled (duplicate stop) — nothing to debit.
                        return Mono.just(balance);
                    }
                    BigDecimal debit = totalSum != null ? totalSum : BigDecimal.ZERO;
                    BigDecimal newBalance = balance.getBalance().subtract(debit);
                    boolean clamped = false;
                    if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                        log.warn("Charging cost {} exceeds balance for user {}, clamping to 0", debit, userId);
                        newBalance = BigDecimal.ZERO;
                        clamped = true;
                    }
                    balance.setBalance(newBalance);
                    balance.setCharging(false);
                    final BigDecimal debited = debit;
                    final BigDecimal finalBalance = newBalance;
                    final boolean wasClamped = clamped;
                    return balanceRepository.save(balance)
                            .doOnSuccess(saved -> {
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("debit", debited);
                                payload.put("newBalance", finalBalance);
                                payload.put("reason", "CHARGING");
                                if (wasClamped) {
                                    payload.put("clampedToZero", true);
                                }
                                auditPublisher.publishBalance("DEBIT", userId, wasClamped ? "WARN" : "INFO",
                                        "Charging settlement -" + debited, payload);
                            });
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.error("Balance not found for user {} on charging stop", userId)));
    }
}
