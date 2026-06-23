package charg.ing.stations.service;

import charg.ing.stations.dto.request.*;
import charg.ing.stations.dto.response.AuthResponse;
import charg.ing.stations.entity.PasswordResetToken;
import charg.ing.stations.entity.User;
import charg.ing.stations.entity.VerificationToken;
import charg.ing.stations.entity.enums.UserRole;
import charg.ing.stations.event.UserEvent;
import charg.ing.stations.event.enums.UserEventType;
import charg.ing.stations.exception.InvalidTokenException;
import charg.ing.stations.exception.UserAlreadyExistsException;
import charg.ing.stations.exception.UserNotFoundException;
import charg.ing.stations.repository.PasswordResetTokenRepository;
import charg.ing.stations.repository.UserRepository;
import charg.ing.stations.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final TokenService tokenService;
    private final KafkaEventProducer kafkaEventProducer;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public Mono<AuthResponse> register(RegisterRequest request, String ipAddress, String userAgent) {
        return userRepository.existsByEmail(request.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Registration failed: User with email {} already exists", request.getEmail());
                        return Mono.error(new UserAlreadyExistsException("User with this email already exists"));
                    }

                    if (request.getPhone() != null && !request.getPhone().isBlank()) {
                        return userRepository.existsByPhone(request.getPhone())
                                .flatMap(phoneExists -> {
                                    if (phoneExists) {
                                        return Mono.error(new UserAlreadyExistsException("User with this phone already exists"));
                                    }
                                    return proceedWithRegistration(request, ipAddress, userAgent);
                                });
                    }

                    return proceedWithRegistration(request, ipAddress, userAgent);
                });
    }

    private Mono<AuthResponse> proceedWithRegistration(RegisterRequest request, String ipAddress, String userAgent) {
        // Определяем роль (по умолчанию USER)
        String role = (request.getRole() != null && !request.getRole().isBlank())
                ? request.getRole().toUpperCase()
                : "USER";

        // Валидируем роль
        try {
            UserRole userRole = UserRole.valueOf(role);
            if (userRole == UserRole.ADMIN || userRole == UserRole.SPECIALIST) {
                // Только существующие админы могут создавать других админов/специалистов
                log.warn("Attempt to register with privileged role: {}", role);
                role = "USER"; // Понижаем до обычного пользователя
            }
        } catch (IllegalArgumentException e) {
            role = "USER"; // Неизвестная роль -> USER
        }

        final String finalRole = role;

        return Mono.fromCallable(() -> keycloakService.createUser(
                        request.getEmail(),
                        request.getPassword(),
                        request.getFirstName(),
                        request.getLastName(),
                        request.getPhone(),
                        finalRole
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> {
                    log.error("Failed to create user in Keycloak: {}", request.getEmail(), e);
                    return new RuntimeException("Failed to create user in identity provider");
                })
                .flatMap(keycloakId -> {
                    User user = User.builder()
                            .keycloakId(keycloakId)
                            .email(request.getEmail())
                            .phone(request.getPhone())
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .role(UserRole.fromString(finalRole))
                            .emailVerified(false)
                            .phoneVerified(false)
                            .active(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return userRepository.save(user)
                            .doOnSuccess(savedUser -> log.info("User registered successfully: {}", savedUser.getEmail()))
                            .flatMap(savedUser -> {
                                UserEvent event = UserEvent.builder()
                                        .eventType(UserEventType.USER_REGISTERED)
                                        .userId(savedUser.getId())
                                        .keycloakId(savedUser.getKeycloakId())
                                        .userEmail(savedUser.getEmail())
                                        .userRole(savedUser.getRole().name())
                                        .timestamp(LocalDateTime.now())
                                        .ipAddress(ipAddress)
                                        .userAgent(userAgent)
                                        .metadata(java.util.Map.of("source", "user-service"))
                                        .build();

                                return kafkaEventProducer.sendUserEvent(event)
                                        .then(generateEmailVerificationToken(savedUser))
                                        .thenReturn(AuthResponse.builder()
                                                .user(AuthResponse.UserResponse.builder()
                                                        .id(savedUser.getId())
                                                        .email(savedUser.getEmail())
                                                        .phone(savedUser.getPhone())
                                                        .firstName(savedUser.getFirstName())
                                                        .lastName(savedUser.getLastName())
                                                        .role(savedUser.getRole().name())
                                                        .emailVerified(false)
                                                        .phoneVerified(false)
                                                        .build())
                                                .build());
                            });
                });
    }

    public Mono<AuthResponse> login(String email, String password, String ipAddress, String userAgent) {
        return userRepository.findActiveByEmail(email)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Login failed: User not found or inactive - {}", email);
                    return Mono.error(new UserNotFoundException("Invalid credentials"));
                }))
                .flatMap(user -> {
                    if (!user.getEmailVerified()) {
                        log.warn("Login blocked for unverified email: {}", email);
                        return Mono.error(new RuntimeException("Email not verified. Please check your inbox and confirm your email address."));
                    }

                    return Mono.fromCallable(() -> {
                                Keycloak keycloak = keycloakService.getUserKeycloakInstance(email, password);
                                try {
                                    return keycloak.tokenManager().getAccessToken();
                                } finally {
                                    keycloak.close();
                                }
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(e -> {
                                log.error("Login failed for user: {}", email, e);
                                userRepository.findByEmail(email).subscribe(foundUser -> {
                                    UserEvent event = UserEvent.builder()
                                            .eventType(UserEventType.LOGIN_FAILED)
                                            .userId(foundUser.getId())
                                            .userEmail(foundUser.getEmail())
                                            .timestamp(LocalDateTime.now())
                                            .ipAddress(ipAddress)
                                            .userAgent(userAgent)
                                            .metadata(java.util.Map.of("error", e.getMessage()))
                                            .build();
                                    kafkaEventProducer.sendUserEvent(event).subscribe();
                                });
                                return Mono.error(new RuntimeException("Invalid credentials"));
                            })
                            .flatMap(tokenResponse -> {
                                user.setLastLoginAt(LocalDateTime.now());
                                return userRepository.save(user)
                                        .map(updatedUser -> {
                                            UserEvent event = UserEvent.builder()
                                                    .eventType(UserEventType.LOGIN_SUCCESS)
                                                    .userId(updatedUser.getId())
                                                    .userEmail(updatedUser.getEmail())
                                                    .userRole(updatedUser.getRole().name())
                                                    .timestamp(LocalDateTime.now())
                                                    .ipAddress(ipAddress)
                                                    .userAgent(userAgent)
                                                    .build();
                                            kafkaEventProducer.sendUserEvent(event).subscribe();
                                            return AuthResponse.builder()
                                                    .accessToken(tokenResponse.getToken())
                                                    .refreshToken(tokenResponse.getRefreshToken())
                                                    .tokenType(tokenResponse.getTokenType())
                                                    .expiresIn(tokenResponse.getExpiresIn())
                                                    .scope(tokenResponse.getScope())
                                                    .user(AuthResponse.UserResponse.builder()
                                                            .id(updatedUser.getId())
                                                            .email(updatedUser.getEmail())
                                                            .phone(updatedUser.getPhone())
                                                            .firstName(updatedUser.getFirstName())
                                                            .lastName(updatedUser.getLastName())
                                                            .role(updatedUser.getRole().name())
                                                            .emailVerified(updatedUser.getEmailVerified())
                                                            .phoneVerified(updatedUser.getPhoneVerified())
                                                            .build())
                                                    .build();
                                        });
                            });
                });
    }

    public Mono<AuthResponse> refreshToken(String refreshToken) {
        return tokenService.refreshAccessToken(refreshToken)
                .doOnSuccess(response -> log.info("Token refreshed successfully"))
                .doOnError(error -> log.error("Token refresh failed", error));
    }

    public Mono<Boolean> checkEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public Mono<Void> initiateEmailVerification(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                .flatMap(this::generateEmailVerificationToken);
    }

    private Mono<Void> generateEmailVerificationToken(User user) {
        // Проверяем, есть ли уже активный токен
        return verificationTokenRepository.findActiveByUserIdAndType(
                        user.getId(), VerificationToken.TokenType.EMAIL_VERIFICATION)
                .flatMap(existingToken -> {
                    // Если токен уже существует и еще активен, используем его
                    log.info("Active verification token already exists for user: {}", user.getEmail());
                    return sendVerificationEmail(user, existingToken.getToken());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Создаем новый токен
                    String token = UUID.randomUUID().toString();
                    LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

                    VerificationToken verificationToken = VerificationToken.builder()
                            .token(token)
                            .userId(user.getId())
                            .tokenType(VerificationToken.TokenType.EMAIL_VERIFICATION)
                            .expiresAt(expiresAt)
                            .used(false)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return verificationTokenRepository.save(verificationToken)
                            .flatMap(savedToken -> {
                                log.info("Verification token generated for user: {}", user.getEmail());
                                return sendVerificationEmail(user, token);
                            });
                }));
    }

    private Mono<Void> sendVerificationEmail(User user, String token) {
        UserEvent event = UserEvent.builder()
                .eventType(UserEventType.EMAIL_VERIFICATION_REQUESTED)
                .userId(user.getId())
                .userEmail(user.getEmail())
                .timestamp(LocalDateTime.now())
                .metadata(java.util.Map.of("token", token))
                .build();

        return kafkaEventProducer.sendNotificationEvent(event);
    }

    @Transactional
    public Mono<Void> verifyEmail(String token) {
        return verificationTokenRepository.findByToken(token)
                .switchIfEmpty(Mono.error(new InvalidTokenException("Invalid verification token")))
                .flatMap(verificationToken -> {
                    if (verificationToken.getUsed()) {
                        return Mono.error(new InvalidTokenException("Token already used"));
                    }

                    if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new InvalidTokenException("Token expired"));
                    }

                    return userRepository.findById(verificationToken.getUserId())
                            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                            .flatMap(user -> {
                                user.setEmailVerified(true);
                                return Mono.fromRunnable(
                                                () -> keycloakService.updateEmailVerified(user.getKeycloakId(), true))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .onErrorMap(e -> new RuntimeException(
                                                "Failed to update email verification in Keycloak", e))
                                        .then(verificationTokenRepository.markAsUsed(token))
                                        .then(userRepository.save(user))
                                        .doOnSuccess(savedUser -> {
                                            log.info("Email verified for user: {}", savedUser.getEmail());
                                            UserEvent event = UserEvent.builder()
                                                    .eventType(UserEventType.EMAIL_VERIFIED)
                                                    .userId(savedUser.getId())
                                                    .userEmail(savedUser.getEmail())
                                                    .timestamp(LocalDateTime.now())
                                                    .build();
                                            kafkaEventProducer.sendUserEvent(event).subscribe();
                                        });
                            });
                })
                .then();
    }

    /**
     * Инициирует процесс сброса пароля: генерирует токен, сохраняет его и отправляет событие в Kafka.
     * @param email email пользователя, запросившего сброс
     * @return Mono<Void>
     */
    @Transactional
    public Mono<Void> initiatePasswordReset(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found with email: " + email)))
                .flatMap(this::createPasswordResetToken)
                .flatMap(token -> sendPasswordResetEvent(email, token))
                .doOnSuccess(v -> log.info("Password reset initiated for email: {}", email))
                .doOnError(e -> log.error("Failed to initiate password reset for email: {}", email, e));
    }

    /**
     * Создаёт токен сброса пароля для пользователя.
     * Если уже есть активный (неиспользованный и не истекший) токен, используем его, иначе создаём новый.
     * @param user пользователь
     * @return Mono с токеном (строкой)
     */
    private Mono<String> createPasswordResetToken(User user) {
        return verificationTokenRepository.findActiveByUserIdAndType(
                        user.getId(), VerificationToken.TokenType.PASSWORD_RESET)
                .map(VerificationToken::getToken)
                .switchIfEmpty(Mono.defer(() -> {
                    String newToken = UUID.randomUUID().toString();
                    LocalDateTime expiresAt = LocalDateTime.now().plusHours(24); // срок действия 24 часа

                    VerificationToken tokenEntity = VerificationToken.builder()
                            .token(newToken)
                            .userId(user.getId())
                            .tokenType(VerificationToken.TokenType.PASSWORD_RESET)
                            .expiresAt(expiresAt)
                            .used(false)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return verificationTokenRepository.save(tokenEntity)
                            .map(VerificationToken::getToken);
                }));
    }

    /**
     * Отправляет событие о запросе сброса пароля в Kafka (notification-service).
     * @param email email пользователя
     * @param token токен сброса
     * @return Mono<Void>
     */
    private Mono<Void> sendPasswordResetEvent(String email, String token) {
        UserEvent event = UserEvent.builder()
                .eventType(UserEventType.PASSWORD_RESET_REQUESTED)
                .userEmail(email)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of("token", token))
                .build();

        return kafkaEventProducer.sendNotificationEvent(event);
    }

    /**
     * Завершает сброс пароля: проверяет токен, обновляет пароль в Keycloak и помечает токен использованным.
     * @param token токен сброса
     * @param newPassword новый пароль
     * @return Mono<Void>
     */
    @Transactional
    public Mono<Void> resetPassword(String token, String newPassword) {
        return verificationTokenRepository.findByToken(token)
                .switchIfEmpty(Mono.error(new InvalidTokenException("Invalid password reset token")))
                .flatMap(verificationToken -> {
                    // Проверка типа токена
                    if (verificationToken.getTokenType() != VerificationToken.TokenType.PASSWORD_RESET) {
                        return Mono.error(new InvalidTokenException("Token is not for password reset"));
                    }
                    // Проверка, использован ли
                    if (verificationToken.getUsed()) {
                        return Mono.error(new InvalidTokenException("Token already used"));
                    }
                    // Проверка срока действия
                    if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new InvalidTokenException("Token expired"));
                    }

                    return userRepository.findById(verificationToken.getUserId())
                            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                            .flatMap(user -> {
                                // Обновляем пароль в Keycloak
                                try {
                                    keycloakService.resetPassword(user.getKeycloakId(), newPassword);
                                } catch (Exception e) {
                                    log.error("Keycloak password reset failed for user: {}", user.getEmail(), e);
                                    return Mono.error(new RuntimeException("Failed to update password in Keycloak", e));
                                }

                                // Помечаем токен как использованный
                                verificationToken.setUsed(true);
                                return verificationTokenRepository.save(verificationToken)
                                        .then(Mono.fromRunnable(() -> {
                                            log.info("Password reset completed for user: {}", user.getEmail());

                                            // Отправляем событие об успешном сбросе (опционально)
                                            UserEvent event = UserEvent.builder()
                                                    .eventType(UserEventType.PASSWORD_RESET_COMPLETED)
                                                    .userEmail(user.getEmail())
                                                    .timestamp(LocalDateTime.now())
                                                    .build();
                                            kafkaEventProducer.sendUserEvent(event).subscribe();
                                        }));
                            });
                })
                .then();
    }

    public Mono<User> getUserById(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")));
    }

    public Mono<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")));
    }

    @Transactional
    public Mono<User> updateUser(Long id, UpdateUserRequest request) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                .flatMap(user -> {
                    boolean changed = false;

                    if (request.getFirstName() != null && !request.getFirstName().equals(user.getFirstName())) {
                        user.setFirstName(request.getFirstName());
                        changed = true;
                    }
                    if (request.getLastName() != null && !request.getLastName().equals(user.getLastName())) {
                        user.setLastName(request.getLastName());
                        changed = true;
                    }
                    if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
                        // Проверяем, не используется ли телефон другим пользователем
                        return userRepository.existsByPhone(request.getPhone())
                                .flatMap(phoneExists -> {
                                    if (phoneExists && !user.getPhone().equals(request.getPhone())) {
                                        return Mono.error(new UserAlreadyExistsException("Phone number already in use"));
                                    }

                                    user.setPhone(request.getPhone());
                                    user.setPhoneVerified(false); // Сбрасываем верификацию при смене телефона

                                    return userRepository.save(user)
                                            .doOnSuccess(updatedUser -> {
                                                log.info("User updated: {}", updatedUser.getEmail());

                                                // Отправляем событие об обновлении
                                                UserEvent event = UserEvent.builder()
                                                        .eventType(UserEventType.USER_UPDATED)
                                                        .userId(updatedUser.getId())
                                                        .userEmail(updatedUser.getEmail())
                                                        .timestamp(LocalDateTime.now())
                                                        .build();

                                                kafkaEventProducer.sendUserEvent(event).subscribe();
                                            });
                                });
                    }

                    if (changed) {
                        return userRepository.save(user)
                                .doOnSuccess(updatedUser -> {
                                    log.info("User updated: {}", updatedUser.getEmail());

                                    // Отправляем событие об обновлении
                                    UserEvent event = UserEvent.builder()
                                            .eventType(UserEventType.USER_UPDATED)
                                            .userId(updatedUser.getId())
                                            .userEmail(updatedUser.getEmail())
                                            .timestamp(LocalDateTime.now())
                                            .build();

                                    kafkaEventProducer.sendUserEvent(event).subscribe();
                                });
                    }

                    return Mono.just(user);
                });
    }

    @Transactional
    public Mono<Void> deactivateUser(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                .flatMap(user -> {
                    user.setActive(false);
                    return userRepository.save(user)
                            .doOnSuccess(deactivatedUser -> {
                                log.info("User deactivated: {}", deactivatedUser.getEmail());

                                UserEvent event = UserEvent.builder()
                                        .eventType(UserEventType.USER_DEACTIVATED)
                                        .userId(deactivatedUser.getId())
                                        .userEmail(deactivatedUser.getEmail())
                                        .timestamp(LocalDateTime.now())
                                        .build();

                                kafkaEventProducer.sendUserEvent(event).subscribe();
                            });
                })
                .then();
    }


    @Transactional
    public Mono<User> activateUser(Long id) { // <--- Изменяем возвращаемый тип на Mono<User>
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                .flatMap(user -> {
                    user.setActive(true);
                    return userRepository.save(user);
                })
                .flatMap(activatedUser -> {
                    // 1. Создаем событие
                    UserEvent event = UserEvent.builder()
                            .eventType(UserEventType.USER_ACTIVATED)
                            .userId(activatedUser.getId())
                            .userEmail(activatedUser.getEmail())
                            .timestamp(LocalDateTime.now())
                            .build();

                    // 2. Отправляем событие в Kafka и после успешной отправки
                    //    возвращаем исходный объект activatedUser
                    return kafkaEventProducer.sendUserEvent(event)
                            .thenReturn(activatedUser); // <--- Ключевая строка!
                });
    }

//    @Transactional
//    public Mono<Void> activateUser(Long id) {
//        return userRepository.findById(id)
//                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
//                .flatMap(user -> {
//                    user.setActive(true);
//                    return userRepository.save(user)
//                            .doOnSuccess(activatedUser -> {
//                                log.info("User activated: {}", activatedUser.getEmail());
//
//                                UserEvent event = UserEvent.builder()
//                                        .eventType(UserEventType.USER_ACTIVATED)
//                                        .userId(activatedUser.getId())
//                                        .userEmail(activatedUser.getEmail())
//                                        .timestamp(LocalDateTime.now())
//                                        .build();
//
//                                kafkaEventProducer.sendUserEvent(event).subscribe();
//                            });
//                })
//                .then();
//    }

    @Transactional
    public Mono<User> changeUserRole(Long id, UserRole newRole) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                .flatMap(user -> {
                    UserRole oldRole = user.getRole();
                    user.setRole(newRole);

                    return userRepository.save(user)
                            .doOnSuccess(updatedUser -> {
                                log.info("User role changed from {} to {} for user: {}",
                                        oldRole, newRole, updatedUser.getEmail());

                                // Обновляем роль в Keycloak
                                keycloakService.assignRole(updatedUser.getKeycloakId(), newRole.name());

                                // Отправляем событие
                                UserEvent event = UserEvent.builder()
                                        .eventType(UserEventType.ROLE_CHANGED)
                                        .userId(updatedUser.getId())
                                        .userEmail(updatedUser.getEmail())
                                        .userRole(newRole.name())
                                        .timestamp(LocalDateTime.now())
                                        .metadata(java.util.Map.of(
                                                "oldRole", oldRole.name(),
                                                "newRole", newRole.name()
                                        ))
                                        .build();

                                kafkaEventProducer.sendUserEvent(event).subscribe();
                            });
                });
    }


    public Flux<User> getAllUsers() {
        return userRepository.findAll()
                .doOnNext(user -> log.debug("Retrieved user: {}", user.getEmail()));
    }

    public Flux<User> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role)
                .doOnNext(user -> log.debug("Retrieved {} user: {}", role, user.getEmail()));
    }

    public Flux<User> searchUsersByEmail(String emailPattern) {
        return userRepository.findByEmailContaining(emailPattern)
                .doOnNext(user -> log.debug("Found user by email pattern: {}", user.getEmail()));
    }

    public Mono<Long> countUsers() {
        return userRepository.count()
                .doOnSuccess(count -> log.debug("Total users count: {}", count));
    }

    public Mono<Map<String, Long>> getUsersStatistics() {
        return Mono.zip(
                userRepository.count(),
                userRepository.countByActive(true),
                userRepository.countByEmailVerified(true),
                userRepository.countByRole(UserRole.USER),
                userRepository.countByRole(UserRole.CONTRACTOR),
                userRepository.countByRole(UserRole.SPECIALIST),
                userRepository.countByRole(UserRole.ADMIN)
        ).map(tuple -> {
            Map<String, Long> stats = new HashMap<>();
            stats.put("total", tuple.getT1());
            stats.put("active", tuple.getT2());
            stats.put("emailVerified", tuple.getT3());
            stats.put("users", tuple.getT4());
            stats.put("contractors", tuple.getT5());
            stats.put("specialists", tuple.getT6());
            stats.put("admins", tuple.getT7());
            return stats;
        });
    }
}