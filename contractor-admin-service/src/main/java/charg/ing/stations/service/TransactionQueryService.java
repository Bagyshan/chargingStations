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

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final ChargeBoxRepository chargeBoxRepository;

    public Flux<TransactionResponse> getAll(Jwt jwt) {
        if (JwtUtils.isContractor(jwt)) {
            return chargeBoxRepository.findByOwnerId(JwtUtils.getUserId(jwt))
                    .map(ChargeBoxEntity::getChargeBoxId)
                    .collectList()
                    .flatMapMany(ids -> ids.isEmpty() ? Flux.empty() : transactionRepository.findByChargeBoxIdIn(ids))
                    .map(this::toResponse);
        }
        return transactionRepository.findAll().map(this::toResponse);
    }

    public Mono<TransactionResponse> getById(Long id, Jwt jwt) {
        return transactionRepository.findById(id)
                .flatMap(entity -> canAccess(entity, jwt)
                        .filter(Boolean::booleanValue)
                        .thenReturn(entity))
                .map(this::toResponse);
    }

    public Mono<TransactionResponse> getByTransactionId(Long transactionId, Jwt jwt) {
        return transactionRepository.findByTransactionId(transactionId)
                .flatMap(entity -> canAccess(entity, jwt)
                        .filter(Boolean::booleanValue)
                        .thenReturn(entity))
                .map(this::toResponse);
    }

    private Mono<Boolean> canAccess(TransactionEntity entity, Jwt jwt) {
        if (JwtUtils.isAdminOrSpecialist(jwt)) return Mono.just(true);
        return chargeBoxRepository.findByChargeBoxId(entity.getChargeBoxId())
                .map(cb -> JwtUtils.getUserId(jwt).equals(cb.getOwnerId()))
                .defaultIfEmpty(false);
    }

    private TransactionResponse toResponse(TransactionEntity e) {
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
                .status(e.getStatus())
                .reason(e.getReason())
                .userId(e.getUserId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
