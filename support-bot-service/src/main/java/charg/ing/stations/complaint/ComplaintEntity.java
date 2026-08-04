package charg.ing.stations.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** Жалоба/обращение из Telegram-бота поддержки. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("support_complaint")
public class ComplaintEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("telegram_user_id")
    private Long telegramUserId;

    @Column("telegram_username")
    private String telegramUsername;

    @Column("telegram_name")
    private String telegramName;

    /** Контакт для связи, оставленный пользователем (телефон/email); может быть null. */
    @Column("contact")
    private String contact;

    @Column("message")
    private String message;

    /** NEW, IN_PROGRESS, RESOLVED. */
    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;
}
