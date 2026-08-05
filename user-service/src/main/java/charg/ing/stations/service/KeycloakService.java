package charg.ing.stations.service;

import charg.ing.stations.exception.IdentityProviderException;
import charg.ing.stations.exception.UserAlreadyExistsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Работа с Keycloak Admin API (создание/обновление/удаление пользователей, роли).
 *
 * ВАЖНО (историческая причина 502 при регистрации): раньше admin-клиент строился ОДИН раз
 * в @PostConstruct и переиспользовался. keycloak-admin-client кэширует admin-токен и refresh-токен;
 * при рестарте Keycloak (в проде он {@code start-dev}, перезапускается на каждом редеплое) или после
 * простоя дольше SSO Session Idle кэшированный токен становится невалидным, а клиент не переавторизуется
 * заново → КАЖДЫЙ admin-вызов кидает сырое 401 (NotAuthorizedException) до самого рестарта user-service.
 * Это 401 маскировалось в UserService под «502 Identity provider is unavailable».
 *
 * Решение: на КАЖДУЮ операцию строим свежий короткоживущий admin-клиент (see {@link #newAdminClient()})
 * и закрываем его. Свежий токен каждый раз → протухание невозможно. Частота admin-операций
 * (регистрация, смена пароля/почты, удаление) низкая, накладные расходы незначительны.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Свежий admin-клиент (realm {@code master}, клиент {@code admin-cli}, password grant).
     * Каждый вызов = новый токен, поэтому нет протухания после рестарта Keycloak / простоя.
     * Клиент реализует {@link AutoCloseable} — использовать только в try-with-resources.
     */
    private Keycloak newAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(adminUsername)
                .password(adminPassword)
                .grantType(OAuth2Constants.PASSWORD)
                .build();
    }

    /** Выполнить действие над realm приложения на свежем admin-клиенте (с гарантированным закрытием). */
    private <T> T withAdminRealm(Function<RealmResource, T> action) {
        try (Keycloak kc = newAdminClient()) {
            return action.apply(kc.realm(realm));
        }
    }

    public String createUser(String email, String password, String firstName,
                             String lastName, String phone, String role) {
        try (Keycloak kc = newAdminClient()) {
            RealmResource realmResource = kc.realm(realm);

            // Проверяем, существует ли пользователь
            List<UserRepresentation> existingUsers = realmResource.users()
                    .searchByEmail(email, true);
            if (!existingUsers.isEmpty()) {
                log.warn("User with email {} already exists in Keycloak", email);
                throw new UserAlreadyExistsException("User with this email already exists");
            }

            // Создаём представление пользователя
            UserRepresentation user = new UserRepresentation();
            user.setUsername(email);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(false);
            user.singleAttribute("phone", phone);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            user.setCredentials(Collections.singletonList(credential));

            // Создаём пользователя
            Response response = realmResource.users().create(user);
            int status = response.getStatus();
            if (status != 201) {
                String kcMessage = extractKeycloakError(response);
                log.error("Failed to create user in Keycloak. Status: {}, message: {}", status, kcMessage);

                if (status == 409) {
                    throw new UserAlreadyExistsException("User with this email already exists");
                }
                if (status == 400) {
                    // Чаще всего — нарушение политики паролей. Отдаём реальное сообщение Keycloak.
                    throw new IdentityProviderException(
                            kcMessage != null ? kcMessage : "Invalid registration data",
                            HttpStatus.BAD_REQUEST);
                }
                throw new IdentityProviderException(
                        "Identity provider is unavailable. Please try again later.",
                        HttpStatus.BAD_GATEWAY);
            }

            String userId = response.getLocation().getPath()
                    .replaceAll(".*/([^/]+)$", "$1");
            log.info("User created in Keycloak with ID: {}", userId);

            // Назначаем роль в рамках того же admin-клиента
            assignRole(realmResource, userId, role);
            return userId;

        } catch (UserAlreadyExistsException | IdentityProviderException e) {
            throw e; // уже осмысленные — пробрасываем как есть
        } catch (Exception e) {
            // keycloak-admin-client оборачивает 401 из грант-запроса в ProcessingException
            // (BearerAuthFilter кидает NotAuthorizedException внутри фильтра) — поэтому классифицируем
            // по всей цепочке cause, а не по типу верхнего исключения.
            if (isAuthFailure(e)) {
                log.error("Keycloak ADMIN authentication FAILED (HTTP 401). Пароль admin '{}' в Keycloak НЕ "
                        + "совпадает с KEYCLOAK_ADMIN_PASSWORD из .env. Bootstrap-пароль применяется ТОЛЬКО к пустой "
                        + "БД Keycloak, поэтому смена .env после первого запуска не меняет реальный пароль — "
                        + "синхронизируй пароли (см. DEPLOY.md).", adminUsername, e);
            } else if (isConnectionFailure(e)) {
                log.error("Keycloak UNREACHABLE at {} (connection/timeout) while registering {}", authServerUrl, email, e);
            } else {
                log.error("Unexpected Keycloak error while creating user {}", email, e);
            }
            throw new IdentityProviderException(
                    "Identity provider is unavailable. Please try again later.", HttpStatus.BAD_GATEWAY);
        }
    }

    /** Есть ли в цепочке причин 401 (протухший/неверный admin-токен). */
    private static boolean isAuthFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof NotAuthorizedException) {
                return true;
            }
            if (c instanceof WebApplicationException wae
                    && wae.getResponse() != null
                    && wae.getResponse().getStatus() == 401) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли в цепочке причин реальная сетевая ошибка (Keycloak недоступен). */
    private static boolean isConnectionFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof java.net.ConnectException
                    || c instanceof java.net.UnknownHostException
                    || c instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** Публичная версия: назначить роль (свежий admin-клиент). */
    public void assignRole(String userId, String roleName) {
        withAdminRealm(realmResource -> {
            assignRole(realmResource, userId, roleName);
            return null;
        });
    }

    /** Внутренняя версия: назначить роль в рамках уже открытого admin-клиента. */
    private void assignRole(RealmResource realmResource, String userId, String roleName) {
        try {
            UserResource userResource = realmResource.users().get(userId);
            String clientUuid = getClientUuid(realmResource);

            RoleRepresentation role = realmResource.clients()
                    .get(clientUuid)
                    .roles()
                    .get(roleName)
                    .toRepresentation();

            userResource.roles()
                    .clientLevel(clientUuid)
                    .add(Collections.singletonList(role));

            log.info("Role {} assigned to user {}", roleName, userId);
        } catch (Exception e) {
            log.error("Failed to assign role {} to user {}", roleName, userId, e);
            throw new RuntimeException("Failed to assign role", e);
        }
    }

    public void updateEmailVerified(String userId, boolean verified) {
        try {
            withAdminRealm(realmResource -> {
                UserResource userResource = realmResource.users().get(userId);
                UserRepresentation user = userResource.toRepresentation();
                user.setEmailVerified(verified);
                userResource.update(user);
                return null;
            });
            log.info("Email verification status updated to {} for user {}", verified, userId);
        } catch (Exception e) {
            log.error("Failed to update email verification status for user {}", userId, e);
            throw new RuntimeException("Failed to update email verification", e);
        }
    }

    /**
     * Обновляет email (и username — в этой системе username == email) пользователя
     * в Keycloak и помечает почту подтверждённой. Вызывается только после того, как
     * пользователь подтвердил владение новым адресом (переход по ссылке из письма).
     *
     * @param keycloakId идентификатор пользователя в Keycloak
     * @param newEmail   новый email
     */
    public void updateEmail(String keycloakId, String newEmail) {
        try {
            withAdminRealm(realmResource -> {
                UserResource userResource = realmResource.users().get(keycloakId);
                UserRepresentation user = userResource.toRepresentation();
                user.setEmail(newEmail);
                user.setUsername(newEmail); // username == email (см. createUser)
                user.setEmailVerified(true);
                userResource.update(user);
                return null;
            });
            log.info("Email updated to {} for Keycloak user {}", newEmail, keycloakId);
        } catch (Exception e) {
            log.error("Failed to update email for Keycloak user {}", keycloakId, e);
            throw new RuntimeException("Failed to update email in Keycloak", e);
        }
    }

    /**
     * Проверяет, занят ли email в Keycloak (точное совпадение). Используется как
     * дополнительная проверка перед сменой почты (помимо проверки в локальной БД).
     */
    public boolean emailExists(String email) {
        try {
            return withAdminRealm(realmResource -> {
                List<UserRepresentation> found = realmResource.users().searchByEmail(email, true);
                return found != null && !found.isEmpty();
            });
        } catch (Exception e) {
            log.error("Failed to check email existence in Keycloak: {}", email, e);
            // Не блокируем поток из-за сбоя проверки — решение остаётся за проверкой в БД.
            return false;
        }
    }

    /**
     * Обновляет пароль пользователя в Keycloak.
     * @param keycloakId идентификатор пользователя в Keycloak
     * @param newPassword новый пароль
     */
    public void resetPassword(String keycloakId, String newPassword) {
        try {
            withAdminRealm(realmResource -> {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(newPassword);
                credential.setTemporary(false);

                realmResource.users().get(keycloakId).resetPassword(credential);
                return null;
            });
            log.info("Password reset successfully for Keycloak user: {}", keycloakId);
        } catch (Exception e) {
            log.error("Failed to reset password for Keycloak user: {}", keycloakId, e);
            throw new RuntimeException("Failed to reset password in Keycloak", e);
        }
    }

    public void deleteUser(String userId) {
        try {
            withAdminRealm(realmResource -> {
                realmResource.users().delete(userId);
                return null;
            });
            log.info("User deleted from Keycloak: {}", userId);
        } catch (Exception e) {
            log.error("Failed to delete user from Keycloak: {}", userId, e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    private String getClientUuid(RealmResource realmResource) {
        return realmResource.clients()
                .findByClientId(clientId)
                .get(0)
                .getId();
    }

    /**
     * Достаёт человекочитаемое сообщение об ошибке из тела ответа Keycloak
     * (поле {@code errorMessage} или {@code error}). Возвращает {@code null},
     * если тело пустое/не разобралось.
     */
    private String extractKeycloakError(Response response) {
        try {
            if (!response.hasEntity()) {
                return null;
            }
            String body = response.readEntity(String.class);
            if (body == null || body.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("errorMessage")) {
                return node.get("errorMessage").asText();
            }
            if (node.hasNonNull("error")) {
                return node.get("error").asText();
            }
            return body;
        } catch (Exception e) {
            log.debug("Could not parse Keycloak error body", e);
            return null;
        }
    }

    public Keycloak getUserKeycloakInstance(String username, String password) {
        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .username(username)
                .password(password)
                .grantType(OAuth2Constants.PASSWORD)
                .build();
    }
}
