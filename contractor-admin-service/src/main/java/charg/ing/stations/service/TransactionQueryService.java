package charg.ing.stations.service;

import charg.ing.stations.dto.response.TransactionResponse;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.TransactionEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.TransactionRepository;
import charg.ing.stations.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final ChargeBoxRepository chargeBoxRepository;

    public Flux<TransactionResponse> getAll(Jwt jwt) {
        if (JwtUtils.isContractor(jwt)) {
            return chargeBoxRepository.findByOwnerId(JwtUtils.getUserId(jwt))
                    .collectList()
                    .flatMapMany(boxes -> {
                        if (boxes.isEmpty()) return Flux.empty();
                        Map<String, BigDecimal> kw = kwCostMap(boxes);
                        List<String> ids = boxes.stream().map(ChargeBoxEntity::getChargeBoxId).toList();
                        return transactionRepository.findByChargeBoxIdIn(ids)
                                .map(t -> toResponse(t, kw.get(t.getChargeBoxId())));
                    });
        }
        // ADMIN/SPECIALIST — тариф каждой станции берём из общей карты (без N+1 запросов).
        return chargeBoxRepository.findAll()
                .collectList()
                .flatMapMany(boxes -> {
                    Map<String, BigDecimal> kw = kwCostMap(boxes);
                    return transactionRepository.findAll()
                            .map(t -> toResponse(t, kw.get(t.getChargeBoxId())));
                });
    }

    public Mono<TransactionResponse> getById(Long id, Jwt jwt) {
        return transactionRepository.findById(id)
                .flatMap(entity -> resolveIfAccessible(entity, jwt));
    }

    public Mono<TransactionResponse> getByTransactionId(Long transactionId, Jwt jwt) {
        return transactionRepository.findByTransactionId(transactionId)
                .flatMap(entity -> resolveIfAccessible(entity, jwt));
    }

    /** Проверка доступа + тариф станции за одно обращение к каталогу. */
    private Mono<TransactionResponse> resolveIfAccessible(TransactionEntity e, Jwt jwt) {
        return chargeBoxRepository.findByChargeBoxId(e.getChargeBoxId())
                .flatMap(cb -> {
                    boolean ok = JwtUtils.isAdminOrSpecialist(jwt)
                            || JwtUtils.getUserId(jwt).equals(cb.getOwnerId());
                    return ok ? Mono.just(toResponse(e, cb.getKwCost())) : Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() ->
                        JwtUtils.isAdminOrSpecialist(jwt)
                                ? Mono.just(toResponse(e, null))
                                : Mono.empty()));
    }

    private Map<String, BigDecimal> kwCostMap(List<ChargeBoxEntity> boxes) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (ChargeBoxEntity b : boxes) {
            if (b.getChargeBoxId() != null) map.put(b.getChargeBoxId(), b.getKwCost());
        }
        return map;
    }

    /**
     * Стоимость зарядки. Если оплата сведена (total_sum > 0) — берём её; иначе оцениваем как
     * энергия(кВт·ч) × тариф: цена транзакции (price_per_kwh), а при её отсутствии — тариф
     * станции (kw_cost). transaction_value хранится в Вт·ч, поэтому делим на 1000.
     */
    private BigDecimal computeTotalSum(TransactionEntity e, BigDecimal kwCostFallback) {
        if (e.getTotalSum() != null && e.getTotalSum().signum() > 0) {
            return e.getTotalSum();
        }
        BigDecimal price = e.getPricePerKwh() != null ? e.getPricePerKwh() : kwCostFallback;
        if (price == null || e.getTransactionValue() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(e.getTransactionValue())
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(price)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private TransactionResponse toResponse(TransactionEntity e, BigDecimal kwCostFallback) {
        return TransactionResponse.builder()
                .id(e.getId())
                .transactionId(e.getTransactionId())
                .chargeBoxId(e.getChargeBoxId())
                .connectorId(e.getConnectorId())
                .startTimestamp(e.getStartTimestamp())
                .startValue(e.getStartValue())
                .stopTimestamp(e.getStopTimestamp())
                .stopValue(e.getStopValue())
                .transactionValue(e.getTransactionValue())
                .totalSum(computeTotalSum(e, kwCostFallback))
                .status(e.getStatus())
                .reason(e.getReason())
                .userId(e.getUserId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
