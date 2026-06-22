package charg.ing.stations.scheduler;

import charg.ing.stations.entity.TopUpStatus;
import charg.ing.stations.repository.TopUpRepository;
import charg.ing.stations.service.TopUpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;

/**
 * Safety net for the webhook: periodically polls O!Dengi for PENDING invoices that are
 * old enough to have been paid, and finalizes them. Makes confirmation reliable even if a
 * result_url callback is missed.
 */
@Component
@Slf4j
public class TopUpReconciliationScheduler {

    private static final Duration MIN_AGE = Duration.ofSeconds(30);
    private static final Duration MAX_AGE = Duration.ofHours(24);

    private final TopUpRepository topUpRepository;
    private final TopUpService topUpService;

    public TopUpReconciliationScheduler(TopUpRepository topUpRepository, TopUpService topUpService) {
        this.topUpRepository = topUpRepository;
        this.topUpService = topUpService;
    }

    @Scheduled(fixedDelayString = "${dengi.reconcile-interval-ms:30000}")
    public void reconcilePending() {
        Instant cutoff = Instant.now().minus(MIN_AGE);
        Instant tooOld = Instant.now().minus(MAX_AGE);
        topUpRepository.findByStatusAndInvoiceIdIsNotNullAndCreatedAtBefore(TopUpStatus.PENDING.name(), cutoff)
                .filter(t -> t.getCreatedAt().isAfter(tooOld))
                .concatMap(topUpService::reconcile)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.error("Top-up reconciliation run failed", err));
    }
}
