package com.fintech.transfer_service.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fintech.transfer_service.data.TransferStatus;
import com.fintech.transfer_service.util.FlexibleLocalDateTimeDeserializer;
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
@Schema(description = "Transfer response data")
public class TransferDto {

    @Schema(description = "Unique transfer identifier", example = "123456")
    private Long id;

    @Schema(description = "Source account identifier", example = "123456789")
    private Long fromAccountId;

    @Schema(description = "Destination account identifier", example = "987654321")
    private Long toAccountId;

    @Schema(description = "Transfer amount", example = "100.50")
    private BigDecimal amount;

    @Schema(description = "Current transfer status")
    private TransferStatus status;

    @Schema(description = "Transfer creation timestamp")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    @Schema(description = "Transfer completion timestamp")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime completedAt;
}
