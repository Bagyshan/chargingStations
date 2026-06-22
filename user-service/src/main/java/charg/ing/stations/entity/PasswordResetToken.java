package charg.ing.stations.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("password_reset_tokens")
public class PasswordResetToken {

    @Id
    private Long id;

    private String token;

    @Column("user_id")
    private Long userId;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    private Boolean used;

    @Column("created_at")
    private LocalDateTime createdAt;
}