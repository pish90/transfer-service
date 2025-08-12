package com.fintech.transfer_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account information")
public class AccountDto {

    @Schema(description = "Account identifier", example = "123456789")
    private Long id;

    @Schema(description = "Current account balance", example = "1000.50")
    private BigDecimal balance;

    @Schema(description = "Account version for optimistic locking", example = "1")
    private Long version;

    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    public AccountDto(Long id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }
}
