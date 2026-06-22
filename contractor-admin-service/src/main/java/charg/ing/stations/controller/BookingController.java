package charg.ing.stations.controller;

import charg.ing.stations.dto.response.BookingResponse;
import charg.ing.stations.service.BookingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Read-only access to station bookings")
public class BookingController {

    private final BookingQueryService bookingQueryService;

    @GetMapping
    @Operation(summary = "Get all bookings (CONTRACTOR sees only bookings at their stations)")
    public Flux<BookingResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return bookingQueryService.getAll(jwt);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking by database id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking found"),
            @ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public Mono<ResponseEntity<BookingResponse>> getById(
            @Parameter(description = "Database primary key") @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return bookingQueryService.getById(id, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-booking-id/{bookingId}")
    @Operation(summary = "Get booking by booking UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking found"),
            @ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public Mono<ResponseEntity<BookingResponse>> getByBookingId(
            @Parameter(description = "Booking unique identifier") @PathVariable UUID bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        return bookingQueryService.getByBookingId(bookingId, jwt)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
