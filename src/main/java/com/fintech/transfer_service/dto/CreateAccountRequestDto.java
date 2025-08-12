package com.fintech.transfer_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Account creation request")
public class CreateAccountRequestDto {

    @NotBlank(message = "Account ID is required")
    @Schema(description = "Unique account identifier", example = "alice")
    private String accountId;

    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.0", message = "Initial balance must be non-negative")
    @Schema(description = "Starting account balance", example = "1000.00")
    private BigDecimal initialBalance;
}
