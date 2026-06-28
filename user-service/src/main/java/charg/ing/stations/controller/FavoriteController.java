package charg.ing.stations.controller;

import charg.ing.stations.dto.response.ApiResponse;
import charg.ing.stations.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Избранные станции пользователя. Возвращает/изменяет только список ID (membership).
 * Полные данные станций (расстояние, статусы, тариф) клиент получает отдельным вызовом
 * {@code POST /api/cached-stations/by-ids} у state-updater-service — вариант композиции A (2 вызова).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Избранные станции пользователя")
@SecurityRequirement(name = "bearerAuth")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "Список ID избранных станций текущего пользователя")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<String>>>> getFavorites(
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        log.info("List favorites for keycloakId={}", keycloakId);

        return favoriteService.listFavoriteIds(keycloakId)
                .collectList()
                .map(ids -> ResponseEntity.ok(ApiResponse.success("Favorites retrieved", ids)));
    }

    @Operation(summary = "Добавить станцию в избранное")
    @PostMapping("/{chargeBoxId}")
    public Mono<ResponseEntity<ApiResponse<Object>>> addFavorite(
            @PathVariable String chargeBoxId,
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        log.info("Add favorite chargeBoxId={} for keycloakId={}", chargeBoxId, keycloakId);

        return favoriteService.addFavorite(keycloakId, chargeBoxId)
                .thenReturn(ResponseEntity.ok(ApiResponse.success("Station added to favorites")));
    }

    @Operation(summary = "Удалить станцию из избранного")
    @DeleteMapping("/{chargeBoxId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<ApiResponse<Object>>> removeFavorite(
            @PathVariable String chargeBoxId,
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        log.info("Remove favorite chargeBoxId={} for keycloakId={}", chargeBoxId, keycloakId);

        return favoriteService.removeFavorite(keycloakId, chargeBoxId)
                .thenReturn(ResponseEntity.ok(ApiResponse.success("Station removed from favorites")));
    }
}
