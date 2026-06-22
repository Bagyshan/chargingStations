package charg.ing.stations.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

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

    private Keycloak keycloakAdmin;
    private RealmResource realmResource;

    @PreDestroy
    public void close() {
        if (keycloakAdmin != null) {
            keycloakAdmin.close();
        }
    }

    @PostConstruct
    public void init() {
        this.keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(adminUsername)
                .password(adminPassword)
                .build();

        this.realmResource = keycloakAdmin.realm(realm);

        log.info("Keycloak admin client initialized for realm: {}", realm);
    }

    public String createUser(String email, String password, String firstName,
                             String lastName, String phone, String role) {

        // Проверяем, существует ли пользователь
        List<UserRepresentation> existingUsers = realmResource.users()
                .searchByEmail(email, true);

        if (!existingUsers.isEmpty()) {
            log.warn("User with email {} already exists in Keycloak", email);
            throw new RuntimeException("User already exists in Keycloak");
        }

        // Создаем представление пользователя
        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(false);

        // Добавляем телефон как атрибут
        user.singleAttribute("phone", phone);

        // Настраиваем учетные данные (пароль)
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));

        // Создаем пользователя
        Response response = realmResource.users().create(user);

        if (response.getStatus() != 201) {
            log.error("Failed to create user in Keycloak. Status: {}", response.getStatus());
            throw new RuntimeException("Failed to create user in Keycloak");
        }

        // Получаем ID созданного пользователя
        String userId = response.getLocation().getPath()
                .replaceAll(".*/([^/]+)$", "$1");

        log.info("User created in Keycloak with ID: {}", userId);

        // Назначаем роль
        assignRole(userId, role);

        return userId;
    }

    public void assignRole(String userId, String roleName) {
        try {
            UserResource userResource = realmResource.users().get(userId);

            // Получаем роль из клиента
            RoleRepresentation role = realmResource.clients()
                    .get(getClientUuid())
                    .roles()
                    .get(roleName)
                    .toRepresentation();

            // Назначаем роль пользователю
            userResource.roles()
                    .clientLevel(getClientUuid())
                    .add(Collections.singletonList(role));

            log.info("Role {} assigned to user {}", roleName, userId);
        } catch (Exception e) {
            log.error("Failed to assign role {} to user {}", roleName, userId, e);
            throw new RuntimeException("Failed to assign role", e);
        }
    }

    public void updateEmailVerified(String userId, boolean verified) {
        try {
            UserResource userResource = realmResource.users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            user.setEmailVerified(verified);
            userResource.update(user);

            log.info("Email verification status updated to {} for user {}", verified, userId);
        } catch (Exception e) {
            log.error("Failed to update email verification status for user {}", userId, e);
            throw new RuntimeException("Failed to update email verification", e);
        }
    }

    /**
     * Обновляет пароль пользователя в Keycloak.
     * @param keycloakId идентификатор пользователя в Keycloak
     * @param newPassword новый пароль
     */
    public void resetPassword(String keycloakId, String newPassword) {
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);

            var userResource = keycloakAdmin.realm(realm).users().get(keycloakId);
            userResource.resetPassword(credential);

            log.info("Password reset successfully for Keycloak user: {}", keycloakId);
        } catch (Exception e) {
            log.error("Failed to reset password for Keycloak user: {}", keycloakId, e);
            throw new RuntimeException("Failed to reset password in Keycloak", e);
        }
    }

    public void deleteUser(String userId) {
        try {
            realmResource.users().delete(userId);
            log.info("User deleted from Keycloak: {}", userId);
        } catch (Exception e) {
            log.error("Failed to delete user from Keycloak: {}", userId, e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    private String getClientUuid() {
        return realmResource.clients()
                .findByClientId(clientId)
                .get(0)
                .getId();
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