package charg.ing.stations.controller;

import charg.ing.stations.dto.request.ChangeRoleRequest;
import charg.ing.stations.dto.response.ApiResponse;
import charg.ing.stations.dto.response.UserProfileResponse;
import charg.ing.stations.dto.response.UserResponse;
import charg.ing.stations.entity.User;
import charg.ing.stations.entity.enums.UserRole;
import charg.ing.stations.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "API для администраторов системы")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    @Operation(summary = "Получить список всех пользователей")
    @GetMapping("/users")
    public Mono<ResponseEntity<ApiResponse<Flux<UserProfileResponse>>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Admin requesting all users, page: {}, size: {}", page, size);

        // TODO: Реализовать пагинацию
        Flux<User> users = userService.getAllUsers();
        Flux<UserProfileResponse> userResponses = users.map(UserProfileResponse::fromUser);

        return Mono.just(ResponseEntity.ok(
                ApiResponse.success("Users retrieved successfully", userResponses)));
    }

    @Operation(summary = "Получить пользователя по ID")
    @GetMapping("/users/{id}")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> getUserById(
            @PathVariable Long id) {

        log.info("Admin requesting user by id: {}", id);

        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(
                        ApiResponse.success("User retrieved successfully",
                                UserProfileResponse.fromUser(user))))
                .doOnError(error -> log.error("Failed to get user by id: {}", id, error));
    }

    @Operation(summary = "Изменить роль пользователя")
    @PutMapping("/users/{id}/role")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> changeUserRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {

        log.info("Admin changing role for user {} to {}", id, request.getRole());

        UserRole newRole;
        try {
            newRole = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid role: " + request.getRole())));
        }

        return userService.changeUserRole(id, newRole)
                .map(user -> ResponseEntity.ok(
                        ApiResponse.success("Role changed successfully",
                                UserProfileResponse.fromUser(user))))
                .doOnSuccess(response -> log.info("Role changed for user: {}", id))
                .doOnError(error -> log.error("Failed to change role for user: {}", id, error));
    }

    @Operation(summary = "Активировать пользователя")
    @PostMapping("/users/{id}/activate")
    public Mono<ResponseEntity<ApiResponse<UserResponse>>> activateUser(
            @PathVariable Long id) {

        log.info("Admin activating user: {}", id);

        return userService.activateUser(id)
                .map(user -> ResponseEntity.ok(
                        ApiResponse.success("User activated successfully",
                                UserResponse.fromUser(user))))
                .doOnSuccess(response -> log.info("User activated: {}", id))
                .doOnError(error -> log.error("Failed to activate user: {}", id, error));
    }

    @Operation(summary = "Деактивировать пользователя")
    @PostMapping("/users/{id}/deactivate")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> deactivateUser(
            @PathVariable Long id) {

        log.info("Admin deactivating user: {}", id);

        return userService.deactivateUser(id)
                .then(userService.getUserById(id))
                .map(user -> ResponseEntity.ok(
                        ApiResponse.success("User deactivated successfully",
                                UserProfileResponse.fromUser(user))))
                .doOnSuccess(response -> log.info("User deactivated: {}", id))
                .doOnError(error -> log.error("Failed to deactivate user: {}", id, error));
    }

    @Operation(summary = "Поиск пользователей по email")
    @GetMapping("/users/search")
    public Mono<ResponseEntity<ApiResponse<Flux<UserProfileResponse>>>> searchUsers(
            @RequestParam String email) {

        log.info("Admin searching users by email: {}", email);

        // TODO: Реализовать полноценный поиск
        Flux<User> users = userService.searchUsersByEmail(email);
        Flux<UserProfileResponse> userResponses = users.map(UserProfileResponse::fromUser);

        return Mono.just(ResponseEntity.ok(
                ApiResponse.success("Users found", userResponses)));
    }

    @Operation(summary = "Получить статистику пользователей")
    @GetMapping("/stats/users")
    public Mono<ResponseEntity<ApiResponse<UserStats>>> getUserStats() {

        log.info("Admin requesting user statistics");

        // TODO: Реализовать сбор статистики
        UserStats stats = UserStats.builder()
                .totalUsers(100)
                .activeUsers(85)
                .inactiveUsers(15)
                .verifiedUsers(70)
                .usersByRole(Map.of(
                        "USER", 75,
                        "CONTRACTOR", 20,
                        "SPECIALIST", 3,
                        "ADMIN", 2
                ))
                .build();

        return Mono.just(ResponseEntity.ok(
                ApiResponse.success("Statistics retrieved", stats)));
    }

    @lombok.Data
    @lombok.Builder
    public static class UserStats {
        private long totalUsers;
        private long activeUsers;
        private long inactiveUsers;
        private long verifiedUsers;
        private Map<String, Integer> usersByRole;
    }
}