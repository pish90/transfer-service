package com.fintech.transfer_service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class LedgerTransferRequest {
    private Long transferId;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;

    public LedgerTransferRequest(Long transferId, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        this.transferId = transferId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
    }
}
