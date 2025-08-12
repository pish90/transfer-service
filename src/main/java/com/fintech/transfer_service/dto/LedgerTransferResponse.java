package com.fintech.transfer_service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class LedgerTransferResponse {
    private String transferId;
    private boolean success;
    private String message;
    private BigDecimal fromBalanceAfter;
    private BigDecimal toBalanceAfter;
    private LocalDateTime timestamp;
}
