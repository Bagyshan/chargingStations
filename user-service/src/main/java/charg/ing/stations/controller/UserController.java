package charg.ing.stations.controller;

import charg.ing.stations.dto.request.ChangePasswordRequest;
import charg.ing.stations.dto.request.UpdateUserRequest;
import charg.ing.stations.dto.response.ApiResponse;
import charg.ing.stations.dto.response.UserProfileResponse;
import charg.ing.stations.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "API для управления пользователями")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Получить список всех пользователей (ADMIN, SPECIALIST, CONTRACTOR)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SPECIALIST', 'CONTRACTOR')")
    @GetMapping("/all")
    public Flux<UserProfileResponse> getAllUsers() {
        log.info("Request to get all users");
        return userService.getAllUsers().map(UserProfileResponse::fromUser);
    }

    @Operation(summary = "Получить профиль текущего пользователя")
    @GetMapping("/profile")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> getProfile(
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        log.info("Profile request for user: {}", email);

        return userService.getUserByEmail(email)
                .map(user -> ResponseEntity
                        .ok(ApiResponse.success(
                                "Profile retrieved successfully",
                                UserProfileResponse.fromUser(user))))
                .doOnSuccess(response -> log.debug("Profile retrieved for: {}", email))
                .doOnError(error -> log.error("Failed to get profile for: {}", email, error));
    }

    @Operation(summary = "Обновить профиль пользователя")
    @PutMapping("/profile")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> updateProfile(
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        log.info("Profile update request for user: {}", email);

        return userService.getUserByEmail(email)
                .flatMap(user -> userService.updateUser(user.getId(), request))
                .map(updatedUser -> ResponseEntity
                        .ok(ApiResponse.success(
                                "Profile updated successfully",
                                UserProfileResponse.fromUser(updatedUser))))
                .doOnSuccess(response -> log.info("Profile updated for: {}", email))
                .doOnError(error -> log.error("Failed to update profile for: {}", email, error));
    }

    @Operation(summary = "Сменить пароль (текущий + новый)")
    @PostMapping("/password")
    public Mono<ResponseEntity<ApiResponse<Object>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        log.info("Password change request for user: {}", email);

        return userService.changePassword(email, request.getCurrentPassword(), request.getNewPassword())
                .thenReturn(ResponseEntity
                        .ok(ApiResponse.success("Password changed successfully", null)))
                .doOnSuccess(response -> log.info("Password changed for: {}", email))
                .doOnError(error -> log.warn("Password change failed for {}: {}", email, error.getMessage()));
    }

    @Operation(summary = "Получить пользователя по ID (только для админов)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> getUserById(
            @PathVariable Long id) {

        log.info("Get user by ID: {}", id);

        return userService.getUserById(id)
                .map(user -> ResponseEntity
                        .ok(ApiResponse.success(
                                "User retrieved successfully",
                                UserProfileResponse.fromUser(user))))
                .doOnSuccess(response -> log.debug("User retrieved: {}", id))
                .doOnError(error -> log.error("Failed to get user: {}", id, error));
    }

    @Operation(summary = "Деактивировать пользователя (только для админов)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<ApiResponse<Object>>> deactivateUser(
            @PathVariable Long id) {

        log.info("Deactivate user: {}", id);

        return userService.deactivateUser(id)
                .thenReturn(ResponseEntity
                        .ok(ApiResponse.success("User deactivated successfully")))
                .doOnSuccess(response -> log.info("User deactivated: {}", id))
                .doOnError(error -> log.error("Failed to deactivate user: {}", id, error));
    }

    @Operation(summary = "Активировать пользователя (только для админов)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<ApiResponse<Object>>> activateUser(
            @PathVariable Long id) {

        log.info("Activate user: {}", id);

        return userService.activateUser(id)
                .thenReturn(ResponseEntity
                        .ok(ApiResponse.success("User activated successfully")))
                .doOnSuccess(response -> log.info("User activated: {}", id))
                .doOnError(error -> log.error("Failed to activate user: {}", id, error));
    }
}