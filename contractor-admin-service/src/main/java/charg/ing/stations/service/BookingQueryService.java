package charg.ing.stations.service;

import charg.ing.stations.dto.response.BookingResponse;
import charg.ing.stations.entity.BookingEntity;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.repository.BookingRepository;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingQueryService {

    private final BookingRepository bookingRepository;
    private final ChargeBoxRepository chargeBoxRepository;

    public Flux<BookingResponse> getAll(Jwt jwt) {
        if (JwtUtils.isContractor(jwt)) {
            return chargeBoxRepository.findByOwnerId(JwtUtils.getUserId(jwt))
                    .map(ChargeBoxEntity::getChargeBoxId)
                    .collectList()
                    .flatMapMany(ids -> ids.isEmpty() ? Flux.empty() : bookingRepository.findByStationIdIn(ids))
                    .map(this::toResponse);
        }
        return bookingRepository.findAll().map(this::toResponse);
    }

    public Mono<BookingResponse> getById(Long id, Jwt jwt) {
        return bookingRepository.findById(id)
                .flatMap(entity -> canAccess(entity, jwt)
                        .filter(Boolean::booleanValue)
                        .thenReturn(entity))
                .map(this::toResponse);
    }

    public Mono<BookingResponse> getByBookingId(UUID bookingId, Jwt jwt) {
        return bookingRepository.findByBookingId(bookingId)
                .flatMap(entity -> canAccess(entity, jwt)
                        .filter(Boolean::booleanValue)
                        .thenReturn(entity))
                .map(this::toResponse);
    }

    private Mono<Boolean> canAccess(BookingEntity entity, Jwt jwt) {
        if (JwtUtils.isAdminOrSpecialist(jwt)) return Mono.just(true);
        return chargeBoxRepository.findByChargeBoxId(entity.getStationId())
                .map(cb -> JwtUtils.getUserId(jwt).equals(cb.getOwnerId()))
                .defaultIfEmpty(false);
    }

    private BookingResponse toResponse(BookingEntity e) {
        return BookingResponse.builder()
                .id(e.getId())
                .bookingId(e.getBookingId())
                .userId(e.getUserId())
                .stationId(e.getStationId())
                .connectorId(e.getConnectorId())
                .pricePerMinute(e.getPricePerMinute())
                .totalSum(e.getTotalSum())
                .totalMinutes(e.getTotalMinutes())
                .startedAt(e.getStartedAt())
                .endedAt(e.getEndedAt())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
