package charg.ing.stations.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class TopUpRequest {
    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal amount;

    public TopUpRequest() {}
    public TopUpRequest(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}