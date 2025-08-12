package com.fintech.transfer_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal transfer request for ledger service communication")
public class TransferRequestDto {

    @NotNull(message = "From account ID is required")
    @Schema(description = "Source account identifier", example = "123456789")
    private Long fromAccountId;

    @NotNull(message = "To account ID is required")
    @Schema(description = "Destination account identifier", example = "987654321")
    private Long toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Schema(description = "Transfer amount", example = "100.50")
    private BigDecimal amount;

    @Schema(description = "Correlation ID for request tracing")
    private String correlationId;

}
