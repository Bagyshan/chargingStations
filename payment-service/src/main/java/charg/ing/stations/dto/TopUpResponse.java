package charg.ing.stations.dto;

import charg.ing.stations.entity.TopUp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What the client needs to render a payment: status + QR/deeplink/paylink links. */
public record TopUpResponse(
        UUID id,
        UUID userId,
        String orderId,
        String invoiceId,
        BigDecimal amount,
        String currency,
        String status,
        String qrUrl,
        String linkApp,
        String paylinkUrl,
        Instant createdAt,
        Instant paidAt
) {
    public static TopUpResponse from(TopUp t) {
        return new TopUpResponse(
                t.getId(), t.getUserId(), t.getOrderId(), t.getInvoiceId(),
                t.getAmount(), t.getCurrency(), t.getStatus(),
                t.getQrUrl(), t.getLinkApp(), t.getPaylinkUrl(),
                t.getCreatedAt(), t.getPaidAt());
    }
}
