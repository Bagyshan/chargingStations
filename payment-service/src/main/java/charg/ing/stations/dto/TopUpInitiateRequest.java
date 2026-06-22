package charg.ing.stations.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request to start a wallet top-up of {@code amount} KGS via O!Dengi. */
public class TopUpInitiateRequest {

    @NotNull
    @DecimalMin(value = "1.00", inclusive = true, message = "Minimum top-up is 1.00 KGS")
    private BigDecimal amount;

    @Size(max = 255)
    private String description;

    public TopUpInitiateRequest() {}

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
