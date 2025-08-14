package com.fintech.transfer_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Transfer creation request")
public class CreateTransferRequestDto {

    @NotNull(message = "From account ID is required")
    @Schema(description = "Source account identifier", example = "123456")
    private Long fromAccountId;

    @NotNull(message = "To account ID is required")
    @Schema(description = "Destination account identifier", example = "654321")
    private Long toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Schema(description = "Transfer amount", example = "100.50")
    private BigDecimal amount;

    @Schema(description = "Optional idempotency key for duplicate prevention", example = "transfer-key-123")
    private String idempotencyKey;

    public CreateTransferRequestDto(Long fromAccountId, Long toAccountId, BigDecimal amount, String idempotencyKey) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }
}
