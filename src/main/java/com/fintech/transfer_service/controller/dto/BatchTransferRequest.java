package com.fintech.transfer_service.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BatchTransferRequest {

    @NotEmpty(message = "Transfers list cannot be empty")
    @Size(max = 20, message = "Cannot process more than 20 transfers at once")
    @Valid
    private List<SingleTransferRequest> transfers;

    public BatchTransferRequest(List<SingleTransferRequest> transfers) {
        this.transfers = transfers;
    }

    public List<SingleTransferRequest> getTransfers() { return transfers; }
    public void setTransfers(List<SingleTransferRequest> transfers) { this.transfers = transfers; }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SingleTransferRequest extends TransferRequest {
        @NotEmpty(message = "Idempotency key is required for batch transfers")
        private String idempotencyKey;
    }
}
