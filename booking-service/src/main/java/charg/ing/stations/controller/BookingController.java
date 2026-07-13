package charg.ing.stations.controller;

import charg.ing.stations.dto.request.BookingRequest;
import charg.ing.stations.dto.responses.AdminBookingResponse;
import charg.ing.stations.dto.responses.BookingCompleteResponse;
import charg.ing.stations.dto.responses.BookingHistoryResponse;
import charg.ing.stations.dto.responses.BookingResponse;
import charg.ing.stations.service.BookingCompletionService;
import charg.ing.stations.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Бронирование", description = "Управление бронированием станций")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;
    private final BookingCompletionService bookingCompletionService;

    @GetMapping
    @Operation(summary = "История бронирований текущего пользователя")
    public Flux<BookingHistoryResponse> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return bookingService.getUserBookings(userId);
    }

    @GetMapping("/all")
    @Operation(summary = "Все бронирования (только ADMIN / SPECIALIST)")
    public Flux<AdminBookingResponse> getAllBookings(@AuthenticationPrincipal Jwt jwt) {
        // Роли берём напрямую из realm_access токена: JwtAuthenticationConverter
        // сервиса не наполняет authorities, поэтому @PreAuthorize здесь не сработал бы.
        if (!hasAdminRole(jwt)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Доступ только для ADMIN или SPECIALIST"));
        }
        return bookingService.getAllBookings();
    }

    @SuppressWarnings("unchecked")
    private boolean hasAdminRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return false;
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof List<?> list)) return false;
        return list.stream()
                .map(Object::toString)
                .map(String::toUpperCase)
                .anyMatch(r -> r.equals("ADMIN") || r.equals("SPECIALIST"));
    }

    @PostMapping
    @Operation(summary = "Создать бронирование")
    public Mono<BookingResponse> createBooking(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody BookingRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return bookingService.createBooking(userId, request);
    }

    @PostMapping("/{bookingId}/complete")
    @Operation(summary = "Завершить бронирование")
    public Mono<BookingCompleteResponse> completeBooking(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID bookingId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return bookingCompletionService.completeBooking(userId, bookingId);
    }
}