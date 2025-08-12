package com.fintech.transfer_service.exception;

import lombok.Getter;

@Getter
public class IdempotencyViolationException extends RuntimeException {

    private final String idempotencyKey;
    private final String existingTransferId;

    public IdempotencyViolationException(String message, String idempotencyKey, String existingTransferId) {
        super(message);
        this.idempotencyKey = idempotencyKey;
        this.existingTransferId = existingTransferId;
    }
}
