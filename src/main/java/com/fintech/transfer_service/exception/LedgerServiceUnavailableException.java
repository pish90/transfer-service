package com.fintech.transfer_service.exception;

import lombok.Getter;

@Getter
public class LedgerServiceUnavailableException extends RuntimeException {
    private final String correlationId;

    public LedgerServiceUnavailableException(String message) {
        super(message);
        this.correlationId = null;
    }

    public LedgerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.correlationId = null;
    }

    public LedgerServiceUnavailableException(String message, String correlationId) {
        super(message);
        this.correlationId = correlationId;
    }

    public LedgerServiceUnavailableException(String message, Throwable cause, String correlationId) {
        super(message, cause);
        this.correlationId = correlationId;
    }
}
