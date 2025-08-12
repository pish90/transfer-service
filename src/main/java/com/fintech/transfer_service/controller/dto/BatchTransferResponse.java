package com.fintech.transfer_service.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BatchTransferResponse {
    private List<TransferResponse> transfers;
    private String correlationId;
    private int totalCount;
    private long successCount;
    private long failedCount;

    public BatchTransferResponse(List<TransferResponse> transfers, String correlationId) {
        this.transfers = transfers;
        this.correlationId = correlationId;
        this.totalCount = transfers.size();
        this.successCount = transfers.stream()
                .mapToLong(t -> t.getStatus().name().equals("COMPLETED") ? 1 : 0)
                .sum();
        this.failedCount = transfers.stream()
                .mapToLong(t -> t.getStatus().name().equals("FAILED") ? 1 : 0)
                .sum();
    }
}