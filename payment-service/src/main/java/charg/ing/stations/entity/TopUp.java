package charg.ing.stations.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single wallet top-up attempt and its O!Dengi invoice — also serves as top-up history.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "top_up")
public class TopUp implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("order_id")
    private String orderId;

    @Column("invoice_id")
    private String invoiceId;

    @Column("trans_id")
    private String transId;

    /** Amount in KGS (som). */
    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    /** TopUpStatus name. */
    @Column("status")
    private String status;

    @Column("description")
    private String description;

    @Column("qr_url")
    private String qrUrl;

    @Column("link_app")
    private String linkApp;

    @Column("paylink_url")
    private String paylinkUrl;

    @Column("test")
    private boolean test;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("paid_at")
    private Instant paidAt;

    /** Tells Spring Data this is a fresh row (we assign UUIDs ourselves). */
    @org.springframework.data.annotation.Transient
    @lombok.Builder.Default
    private boolean newEntity = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
