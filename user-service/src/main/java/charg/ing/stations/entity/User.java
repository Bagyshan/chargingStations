package charg.ing.stations.entity;

import charg.ing.stations.entity.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {

    @Id
    private Long id;

    @Column("keycloak_id")
    private String keycloakId;

    private String email;
    private String phone;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    private UserRole role;

    @Column("email_verified")
    private Boolean emailVerified;

    @Column("phone_verified")
    private Boolean phoneVerified;

    private Boolean active;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("last_login_at")
    private LocalDateTime lastLoginAt;

    @Transient
    private String accessToken;

    @Transient
    private String refreshToken;
}