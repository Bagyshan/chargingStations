package charg.ing.stations.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("verification_tokens")
public class VerificationToken {

    @Id
    private Long id;

    private String token;

    @Column("user_id")
    private Long userId;

    @Column("token_type")
    private TokenType tokenType;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    private Boolean used;

    @Column("created_at")
    private LocalDateTime createdAt;

    public enum TokenType {
        EMAIL_VERIFICATION,
        PHONE_VERIFICATION,
        PASSWORD_RESET
    }
}