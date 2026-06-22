package charg.ing.stations.controller;

import charg.ing.stations.dto.request.BookingRequest;
import charg.ing.stations.dto.responses.BookingCompleteResponse;
import charg.ing.stations.dto.responses.BookingResponse;
import charg.ing.stations.service.BookingCompletionService;
import charg.ing.stations.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Бронирование", description = "Управление бронированием станций")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;
    private final BookingCompletionService bookingCompletionService;

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