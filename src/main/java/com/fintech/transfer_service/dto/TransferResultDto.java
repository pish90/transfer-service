package com.fintech.transfer_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of transfer operation from ledger service")
public class TransferResultDto {

    @Schema(description = "Whether the transfer was successful", example = "true")
    private boolean success;

    @Schema(description = "Result message or error description", example = "Transfer completed successfully")
    private String message;

    @Schema(description = "Optional error code for failed transfers", example = "INSUFFICIENT_FUNDS")
    private String errorCode;

    public TransferResultDto(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
