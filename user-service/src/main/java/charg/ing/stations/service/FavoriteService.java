package charg.ing.stations.service;

import charg.ing.stations.entity.UserFavoriteStation;
import charg.ing.stations.repository.UserFavoriteStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Управляет избранными станциями пользователя (membership). Данные самих станций здесь не хранятся —
 * клиент обогащает список ID из кэша state-updater-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteStationRepository repository;

    /** Список chargeBoxId избранных станций пользователя (новые сверху). */
    public Flux<String> listFavoriteIds(String keycloakId) {
        return repository.findByKeycloakIdOrderByCreatedAtDesc(keycloakId)
                .map(UserFavoriteStation::getChargeBoxId);
    }

    /** Идемпотентно добавляет станцию в избранное. Повторный вызов не создаёт дубликат. */
    public Mono<Void> addFavorite(String keycloakId, String chargeBoxId) {
        return repository.existsByKeycloakIdAndChargeBoxId(keycloakId, chargeBoxId)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.debug("Favorite already exists: keycloakId={}, chargeBoxId={}", keycloakId, chargeBoxId);
                        return Mono.empty();
                    }
                    UserFavoriteStation favorite = UserFavoriteStation.builder()
                            .keycloakId(keycloakId)
                            .chargeBoxId(chargeBoxId)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return repository.save(favorite)
                            .doOnSuccess(saved -> log.info("Added favorite: keycloakId={}, chargeBoxId={}",
                                    keycloakId, chargeBoxId))
                            .then();
                });
    }

    /** Удаляет станцию из избранного. Если её не было — операция тихо завершается. */
    public Mono<Void> removeFavorite(String keycloakId, String chargeBoxId) {
        return repository.deleteByKeycloakIdAndChargeBoxId(keycloakId, chargeBoxId)
                .doOnSuccess(v -> log.info("Removed favorite: keycloakId={}, chargeBoxId={}",
                        keycloakId, chargeBoxId));
    }
}
