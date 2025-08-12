package com.fintech.transfer_service.exception;

import lombok.Getter;

@Getter
public class TransferException extends RuntimeException {
    private final String errorCode;
    private final String correlationId;

    public TransferException(String message) {
        super(message);
        this.errorCode = null;
        this.correlationId = null;
    }

    public TransferException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.correlationId = null;
    }

    public TransferException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.correlationId = null;
    }

    public TransferException(String message, String errorCode, String correlationId) {
        super(message);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
    }

    public TransferException(String message, Throwable cause, String errorCode, String correlationId) {
        super(message, cause);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
    }
}
