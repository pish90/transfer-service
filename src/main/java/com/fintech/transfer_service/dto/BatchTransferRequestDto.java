package com.fintech.transfer_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransferRequestDto {

    @NotEmpty(message = "Transfer list cannot be empty")
    @Size(max = 100, message = "Maximum 100 transfers allowed per batch")
    @Valid
    @Schema(description = "List of transfers to process")
    private List<CreateTransferRequestDto> transfers;
}
