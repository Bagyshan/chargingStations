package charg.ing.stations.service;

import charg.ing.stations.config.DengiProperties;
import charg.ing.stations.dengi.DengiClient;
import charg.ing.stations.dto.dengi.CreateInvoiceResult;
import charg.ing.stations.dto.dengi.DengiPayment;
import charg.ing.stations.dto.dengi.PaymentCallback;
import charg.ing.stations.dto.dengi.StatusPaymentResult;
import charg.ing.stations.dto.kafka.BalanceUpdatedEvent;
import charg.ing.stations.entity.TopUp;
import charg.ing.stations.entity.TopUpStatus;
import charg.ing.stations.kafka.BalanceEventProducer;
import charg.ing.stations.repository.TopUpRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates wallet top-ups via O!Dengi:
 * create invoice -> payer pays QR/bank -> confirm (webhook or polling) -> credit wallet (once).
 */
@Service
@Slf4j
public class TopUpService {

    private final DengiClient dengiClient;
    private final TopUpRepository topUpRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final DatabaseClient db;
    private final TransactionalOperator tx;
    private final DengiProperties props;
    private final BalanceEventProducer balanceEventProducer;

    public TopUpService(DengiClient dengiClient,
                        TopUpRepository topUpRepository,
                        R2dbcEntityTemplate entityTemplate,
                        DatabaseClient db,
                        TransactionalOperator tx,
                        DengiProperties props,
                        BalanceEventProducer balanceEventProducer) {
        this.dengiClient = dengiClient;
        this.topUpRepository = topUpRepository;
        this.entityTemplate = entityTemplate;
        this.db = db;
        this.tx = tx;
        this.props = props;
        this.balanceEventProducer = balanceEventProducer;
    }

    /**
     * Creates a top-up invoice for the given amount (in KGS) and returns the persisted record
     * enriched with the QR / deeplink / paylink links the client must show to the payer.
     */
    public Mono<TopUp> initiate(UUID userId, BigDecimal amountSom, String description) {
        UUID id = UUID.randomUUID();
        String orderId = id.toString().replace("-", "");
        String desc = (description == null || description.isBlank()) ? "Wallet top-up" : description;
        BigDecimal amount = amountSom.setScale(2, java.math.RoundingMode.HALF_UP);
        long kopecks = amount.movePointRight(2).longValueExact();
        Instant now = Instant.now();

        TopUp pending = TopUp.builder()
                .id(id)
                .userId(userId)
                .orderId(orderId)
                .amount(amount)
                .currency("KGS")
                .status(TopUpStatus.PENDING.name())
                .description(desc)
                .test(props.getTest() == 1)
                .createdAt(now)
                .updatedAt(now)
                .newEntity(true)
                .build();

        return entityTemplate.insert(pending)
                .flatMap(saved -> dengiClient.createInvoice(orderId, kopecks, desc)
                        .flatMap(result -> applyInvoice(saved, result))
                        .onErrorResume(err -> markFailed(saved).then(Mono.error(err))));
    }

    private Mono<TopUp> applyInvoice(TopUp topUp, CreateInvoiceResult result) {
        topUp.setInvoiceId(result.getInvoiceId());
        topUp.setQrUrl(result.getQr());
        topUp.setLinkApp(result.getLinkApp());
        topUp.setPaylinkUrl(result.getPaylinkUrl());
        topUp.setUpdatedAt(Instant.now());
        return db.sql("UPDATE top_up SET invoice_id=:inv, qr_url=:qr, link_app=:la, " +
                        "paylink_url=:pl, updated_at=now() WHERE id=:id")
                .bind("inv", result.getInvoiceId())
                .bind("qr", nullSafe(result.getQr()))
                .bind("la", nullSafe(result.getLinkApp()))
                .bind("pl", nullSafe(result.getPaylinkUrl()))
                .bind("id", topUp.getId())
                .fetch().rowsUpdated()
                .thenReturn(topUp);
    }

    private Mono<Void> markFailed(TopUp topUp) {
        return db.sql("UPDATE top_up SET status='FAILED', updated_at=now() WHERE id=:id AND status='PENDING'")
                .bind("id", topUp.getId())
                .fetch().rowsUpdated().then();
    }

    /** Top-up history for a user (newest first). */
    public Flux<TopUp> history(UUID userId) {
        return topUpRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Mono<TopUp> getByOrderId(String orderId) {
        return topUpRepository.findByOrderId(orderId);
    }

    // ---------------------------------------------------------------------
    // Confirmation paths (webhook + polling) — both funnel into finalize*.
    // ---------------------------------------------------------------------

    /** Processes a result_url webhook from O!Dengi. */
    public Mono<Void> handleCallback(PaymentCallback cb) {
        if (cb.getOrderId() == null || cb.getStatusPay() == null) {
            log.warn("Ignoring O!Dengi callback without order_id/status_pay: {}", cb);
            return Mono.empty();
        }
        if (cb.getStatusPay() == PaymentCallback.STATUS_APPROVED) {
            return finalizeApproved(cb.getOrderId(), cb.getTransId());
        }
        if (cb.getStatusPay() == PaymentCallback.STATUS_CANCELED) {
            return finalizeCanceled(cb.getOrderId());
        }
        log.info("O!Dengi callback with non-final status_pay={} for order {}", cb.getStatusPay(), cb.getOrderId());
        return Mono.empty();
    }

    /** Polls statusPayment for one pending invoice and finalizes it if it reached a final state. */
    public Mono<Void> reconcile(TopUp topUp) {
        return dengiClient.statusPayment(topUp.getInvoiceId(), topUp.getOrderId())
                .flatMap(status -> {
                    Resolution r = resolve(status);
                    return switch (r.status()) {
                        case APPROVED -> finalizeApproved(topUp.getOrderId(), r.transId());
                        case CANCELED -> finalizeCanceled(topUp.getOrderId());
                        default -> Mono.empty();
                    };
                })
                .onErrorResume(err -> {
                    log.warn("Reconcile failed for order {}: {}", topUp.getOrderId(), err.getMessage());
                    return Mono.empty();
                });
    }

    private record Resolution(TopUpStatus status, String transId) {}

    private Resolution resolve(StatusPaymentResult status) {
        List<DengiPayment> payments = status.getPayments();
        if (payments != null) {
            for (DengiPayment p : payments) {
                if ("approved".equalsIgnoreCase(p.getStatus())) {
                    return new Resolution(TopUpStatus.APPROVED, p.getTransId());
                }
            }
            boolean anyCanceled = payments.stream().anyMatch(p -> "canceled".equalsIgnoreCase(p.getStatus()));
            if (anyCanceled) {
                return new Resolution(TopUpStatus.CANCELED, null);
            }
        }
        if ("approved".equalsIgnoreCase(status.getStatus())) {
            return new Resolution(TopUpStatus.APPROVED, null);
        }
        if ("canceled".equalsIgnoreCase(status.getStatus())) {
            return new Resolution(TopUpStatus.CANCELED, null);
        }
        return new Resolution(TopUpStatus.PENDING, null);
    }

    /**
     * Idempotently marks an invoice approved and credits the wallet exactly once.
     * The status guard in the UPDATE makes concurrent webhook+poll safe.
     */
    public Mono<Void> finalizeApproved(String orderId, String transId) {
        // Returns the credited user's id only when THIS call actually performed the credit
        // (status guard => idempotent; empty when already finalized).
        Mono<UUID> work = db.sql("UPDATE top_up SET status='APPROVED', trans_id=:t, paid_at=now(), " +
                        "updated_at=now() WHERE order_id=:o AND status='PENDING'")
                .bind("o", orderId)
                .bind("t", transId == null ? "" : transId)
                .fetch().rowsUpdated()
                .flatMap(rows -> {
                    if (rows == 0) {
                        log.debug("Top-up {} already finalized, skipping credit", orderId);
                        return Mono.empty();
                    }
                    return creditWalletForOrder(orderId);
                });
        // Notify an active booking after the credit is committed (publish outside the tx).
        return work.as(tx::transactional)
                .flatMap(this::publishBalanceUpdateIfActive)
                .then();
    }

    private Mono<UUID> creditWalletForOrder(String orderId) {
        return db.sql("SELECT user_id, amount FROM top_up WHERE order_id=:o")
                .bind("o", orderId)
                .map((row, meta) -> new Object[]{
                        row.get("user_id", UUID.class),
                        row.get("amount", BigDecimal.class)})
                .one()
                .flatMap(arr -> creditWallet((UUID) arr[0], (BigDecimal) arr[1])
                        .doOnSuccess(v -> log.info("Credited wallet {} by {} (order {})", arr[0], arr[1], orderId))
                        .thenReturn((UUID) arr[0]));
    }

    /**
     * If the just-credited user currently has an active booking OR an active charging session, emit a
     * balance-update event to {@code payment.events} so booking-service / station-controll-service can
     * extend the session under the new balance.
     */
    private Mono<UUID> publishBalanceUpdateIfActive(UUID userId) {
        return db.sql("SELECT balance, is_booking, is_charging FROM balance WHERE user_id=:u")
                .bind("u", userId)
                .map((row, meta) -> new Object[]{
                        row.get("balance", BigDecimal.class),
                        row.get("is_booking", Boolean.class),
                        row.get("is_charging", Boolean.class)})
                .one()
                .doOnNext(arr -> {
                    BigDecimal newBalance = (BigDecimal) arr[0];
                    boolean isBooking = Boolean.TRUE.equals(arr[1]);
                    boolean isCharging = Boolean.TRUE.equals(arr[2]);
                    if (isBooking || isCharging) {
                        balanceEventProducer.publishBalanceUpdated(BalanceUpdatedEvent.builder()
                                .userId(userId)
                                .newBalance(newBalance)
                                .timestamp(Instant.now())
                                .build());
                    } else {
                        log.debug("User {} has no active booking/charging; balance-update event skipped", userId);
                    }
                })
                .thenReturn(userId);
    }

    /** Upserts the wallet row and atomically increments the balance. */
    private Mono<Void> creditWallet(UUID userId, BigDecimal amount) {
        return db.sql("INSERT INTO balance(user_id, balance, is_booking) VALUES(:u, :a, false) " +
                        "ON CONFLICT (user_id) DO UPDATE SET balance = balance.balance + EXCLUDED.balance")
                .bind("u", userId)
                .bind("a", amount)
                .fetch().rowsUpdated().then();
    }

    public Mono<Void> finalizeCanceled(String orderId) {
        return db.sql("UPDATE top_up SET status='CANCELED', updated_at=now() " +
                        "WHERE order_id=:o AND status='PENDING'")
                .bind("o", orderId)
                .fetch().rowsUpdated().then();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
