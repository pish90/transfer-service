package com.fintech.transfer_service.controller.dto;

import com.fintech.transfer_service.data.TransferStatus;
import com.fintech.transfer_service.entity.Transfer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TransferResponse {
    private Long id;
    private long fromAccountId;
    private long toAccountId;
    private BigDecimal amount;
    private TransferStatus status;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String correlationId;

    public static TransferResponse fromTransfer(Transfer transfer) {
        TransferResponse response = new TransferResponse();
        response.setId(transfer.getId());
        response.setFromAccountId(transfer.getFromAccountId());
        response.setToAccountId(transfer.getToAccountId());
        response.setAmount(transfer.getAmount());
        response.setStatus(transfer.getStatus());
        response.setMessage(transfer.getMessage());
        response.setCreatedAt(transfer.getCreatedAt());
        response.setCompletedAt(transfer.getCompletedAt());
        return response;
    }
}
