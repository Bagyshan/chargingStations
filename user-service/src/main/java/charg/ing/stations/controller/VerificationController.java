//package charg.ing.stations.controller;
//
//import charg.ing.stations.dto.response.ApiResponse;
//import charg.ing.stations.service.UserService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.security.SecurityRequirement;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import reactor.core.publisher.Mono;
//
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/verification")
//@RequiredArgsConstructor
//@Tag(name = "Verification", description = "API для верификации email и телефона")
//public class VerificationController {
//
//    private final UserService userService;
//
//    @Operation(summary = "Запросить верификацию email")
//    @SecurityRequirement(name = "bearerAuth")
//    @PostMapping("/email/request")
//    public Mono<ResponseEntity<ApiResponse<Object>>> requestEmailVerification(
//            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
//
//        // Извлекаем email из JWT (в реальном приложении)
//        // Для демо используем фиксированный или параметр
//        String email = extractEmailFromToken(authHeader);
//
//        log.info("Requesting email verification for: {}", email);
//
//        return userService.initiateEmailVerification(email)
//                .thenReturn(ResponseEntity.ok(
//                        ApiResponse.success("Verification email sent")))
//                .doOnError(error -> log.error("Failed to request email verification", error));
//    }
//
//    @Operation(summary = "Подтвердить email")
//    @GetMapping("/email/confirm")
//    public Mono<ResponseEntity<ApiResponse<Object>>> confirmEmail(
//            @RequestParam String token) {
//
//        log.info("Confirming email with token");
//
//        return userService.verifyEmail(token)
//                .thenReturn(ResponseEntity.ok(
//                        ApiResponse.success("Email verified successfully")))
//                .doOnSuccess(response -> log.info("Email verification successful"))
//                .doOnError(error -> log.warn("Email verification failed: {}", error.getMessage()));
//    }
//
//    @Operation(summary = "Запросить верификацию телефона")
//    @SecurityRequirement(name = "bearerAuth")
//    @PostMapping("/phone/request")
//    public Mono<ResponseEntity<ApiResponse<Void>>> requestPhoneVerification(
//            @RequestParam String phone) {
//
//        log.info("Requesting phone verification for: {}", phone);
//
//        // TODO: Реализовать логику верификации телефона через SMS
//        return Mono.just(ResponseEntity.ok(
//                ApiResponse.success("SMS verification code sent")));
//    }
//
//    @Operation(summary = "Подтвердить телефон")
//    @SecurityRequirement(name = "bearerAuth")
//    @PostMapping("/phone/confirm")
//    public Mono<ResponseEntity<ApiResponse<Void>>> confirmPhone(
//            @RequestParam String code) {
//
//        log.info("Confirming phone with code");
//
//        // TODO: Реализовать проверку SMS кода
//        return Mono.just(ResponseEntity.ok(
//                ApiResponse.success("Phone verified successfully")));
//    }
//
//    @Operation(summary = "Проверить статус верификации")
//    @SecurityRequirement(name = "bearerAuth")
//    @GetMapping("/status")
//    public Mono<ResponseEntity<ApiResponse<VerificationStatus>>> getVerificationStatus(
//            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
//
//        String email = extractEmailFromToken(authHeader);
//
//        return userService.getUserByEmail(email)
//                .map(user -> ResponseEntity.ok(
//                        ApiResponse.success("Verification status retrieved",
//                                VerificationStatus.builder()
//                                        .emailVerified(user.getEmailVerified())
//                                        .phoneVerified(user.getPhoneVerified())
//                                        .build())))
//                .doOnError(error -> log.error("Failed to get verification status", error));
//    }
//
//    private String extractEmailFromToken(String authHeader) {
//        // В реальном приложении парсим JWT и извлекаем email
//        // Для демо возвращаем placeholder
//        return "user@example.com";
//    }
//
//    @lombok.Data
//    @lombok.Builder
//    public static class VerificationStatus {
//        private Boolean emailVerified;
//        private Boolean phoneVerified;
//    }
//}


package charg.ing.stations.controller;

import charg.ing.stations.dto.request.PasswordResetConfirmRequest;
import charg.ing.stations.dto.request.PasswordResetRequest;
import charg.ing.stations.dto.response.ApiResponse;
import charg.ing.stations.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/verification")
@RequiredArgsConstructor
@Tag(name = "Verification", description = "API для верификации email и телефона")
public class VerificationController {

    private final UserService userService;

    @Operation(summary = "Запросить верификацию email")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/email/request")
    public Mono<ResponseEntity<ApiResponse<Object>>> requestEmailVerification(
            @AuthenticationPrincipal Jwt jwt) {

        // Извлекаем email из JWT
        String email = extractEmailFromJwt(jwt);

        log.info("Requesting email verification for: {}", email);

        return userService.initiateEmailVerification(email)
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.success("Verification email sent")))
                .doOnSuccess(v -> log.info("Verification email requested successfully for: {}", email))
                .doOnError(error -> log.error("Failed to request email verification for: {}", email, error));
    }

    @Operation(summary = "Подтвердить email")
    @GetMapping("/email/confirm")
    public Mono<ResponseEntity<ApiResponse<Object>>> confirmEmail(
            @RequestParam String token) {

        log.info("Confirming email with token: {}", token.substring(0, Math.min(token.length(), 10)) + "...");

        return userService.verifyEmail(token)
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.success("Email verified successfully")))
                .doOnSuccess(response -> log.info("Email verification successful"))
                .doOnError(error -> log.warn("Email verification failed: {}", error.getMessage()));
    }

    @Operation(summary = "Проверить статус верификации")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/status")
    public Mono<ResponseEntity<ApiResponse<VerificationStatus>>> getVerificationStatus(
            @AuthenticationPrincipal Jwt jwt) {

        String email = extractEmailFromJwt(jwt);

        return userService.getUserByEmail(email)
                .map(user -> {
                    log.debug("Retrieved verification status for user: {}", email);
                    return ResponseEntity.ok(
                            ApiResponse.success("Verification status retrieved",
                                    VerificationStatus.builder()
                                            .emailVerified(user.getEmailVerified())
                                            .phoneVerified(user.getPhoneVerified())
                                            .build()));
                })
                .doOnError(error -> log.error("Failed to get verification status for: {}", email, error));
    }

    @Operation(summary = "Повторно отправить email для подтверждения")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/email/resend")
    public Mono<ResponseEntity<ApiResponse<Object>>> resendVerificationEmail(
            @AuthenticationPrincipal Jwt jwt) {

        String email = extractEmailFromJwt(jwt);

        log.info("Resending verification email for: {}", email);

        return userService.initiateEmailVerification(email)
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.success("Verification email resent successfully")))
                .doOnSuccess(v -> log.info("Verification email resent for: {}", email))
                .doOnError(error -> log.error("Failed to resend verification email for: {}", email, error));
    }

    /**
     * Извлекает email из JWT токена
     * Пытается получить из поля "email", затем из "preferred_username"
     */
    private String extractEmailFromJwt(Jwt jwt) {
        // Сначала пробуем получить из стандартного поля email
        String email = jwt.getClaimAsString("email");

        // Если нет, пробуем из preferred_username (часто содержит email)
        if (email == null || email.isEmpty()) {
            email = jwt.getClaimAsString("preferred_username");
        }

        // Если всё ещё нет, пробуем получить из subject (иногда тоже email)
        if (email == null || email.isEmpty()) {
            email = jwt.getSubject();
        }

        // Если ничего не нашли, логируем ошибку
        if (email == null || email.isEmpty()) {
            log.error("Could not extract email from JWT. Claims: {}", jwt.getClaims());
            throw new IllegalStateException("Email not found in JWT token");
        }

        log.debug("Extracted email from JWT: {}", email);
        return email;
    }





    @Operation(summary = "Запросить сброс пароля")
    @PostMapping("/reset-password/request")
    public Mono<ResponseEntity<ApiResponse<Object>>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {

        log.info("Password reset requested for email: {}", request.getEmail());

        return userService.initiatePasswordReset(request.getEmail())
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.success("If the email exists, a password reset link has been sent")))
                .doOnSuccess(v -> log.info("Password reset request processed for email: {}", request.getEmail()))
                .doOnError(e -> log.error("Error processing password reset request for email: {}", request.getEmail(), e));
    }

    @Operation(summary = "Подтвердить сброс пароля (установить новый пароль)")
    @PostMapping("/reset-password/confirm")
    public Mono<ResponseEntity<ApiResponse<Object>>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {

        log.info("Password reset confirmation with token: {}",
                request.getToken().substring(0, Math.min(request.getToken().length(), 10)) + "...");

        return userService.resetPassword(request.getToken(), request.getNewPassword())
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.success("Password has been reset successfully")))
                .doOnSuccess(v -> log.info("Password reset completed successfully"))
                .doOnError(e -> log.warn("Password reset failed: {}", e.getMessage()));
    }


    /**
     * Класс для ответа со статусом верификации
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationStatus {
        private Boolean emailVerified;
        private Boolean phoneVerified;
    }
}