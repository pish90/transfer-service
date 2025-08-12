package com.fintech.transfer_service.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler  {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TransferException.class)
    public ResponseEntity<Map<String, Object>> handleTransferException(TransferException ex) {
        String correlationId = MDC.get("correlationId");
        log.error("Transfer failed [correlationId={}]: {}", correlationId, ex.getMessage(), ex);

        Map<String, Object> errorResponse = createErrorResponse(
                "TRANSFER_FAILED",
                ex.getMessage(),
                correlationId,
                HttpStatus.BAD_REQUEST
        );

        if (ex.getErrorCode() != null) {
            errorResponse.put("errorCode", ex.getErrorCode());
        }

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(LedgerServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleLedgerServiceUnavailable(LedgerServiceUnavailableException ex) {
        String correlationId = MDC.get("correlationId");
        log.error("Ledger service unavailable [correlationId={}]: {}", correlationId, ex.getMessage(), ex);

        Map<String, Object> errorResponse = createErrorResponse(
                "SERVICE_UNAVAILABLE",
                "Transfer service is temporarily unavailable. Please try again later.",
                correlationId,
                HttpStatus.SERVICE_UNAVAILABLE
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(IdempotencyViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyViolation(IdempotencyViolationException ex) {
        String correlationId = MDC.get("correlationId");
        log.warn("Idempotency violation [correlationId={}]: {}", correlationId, ex.getMessage());

        Map<String, Object> errorResponse = createErrorResponse(
                "IDEMPOTENCY_VIOLATION",
                ex.getMessage(),
                correlationId,
                HttpStatus.CONFLICT
        );
        errorResponse.put("idempotencyKey", ex.getIdempotencyKey());
        errorResponse.put("existingTransferId", ex.getExistingTransferId());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String correlationId = MDC.get("correlationId");
        log.warn("Validation failed [correlationId={}]: {}", correlationId, ex.getMessage());

        StringBuilder errorMessage = new StringBuilder("Validation failed: ");
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errorMessage.append(error.getField()).append(" ").append(error.getDefaultMessage()).append("; ")
        );

        Map<String, Object> errorResponse = createErrorResponse(
                "VALIDATION_FAILED",
                errorMessage.toString(),
                correlationId,
                HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String correlationId = MDC.get("correlationId");
        log.warn("Constraint violation [correlationId={}]: {}", correlationId, ex.getMessage());

        Map<String, Object> errorResponse = createErrorResponse(
                "CONSTRAINT_VIOLATION",
                "Request violates business constraints: " + ex.getMessage(),
                correlationId,
                HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String correlationId = MDC.get("correlationId");
        log.warn("Invalid argument [correlationId={}]: {}", correlationId, ex.getMessage());

        Map<String, Object> errorResponse = createErrorResponse(
                "INVALID_ARGUMENT",
                ex.getMessage(),
                correlationId,
                HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        String correlationId = MDC.get("correlationId");
        log.error("Unexpected error [correlationId={}]: {}", correlationId, ex.getMessage(), ex);

        Map<String, Object> errorResponse = createErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                correlationId,
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private Map<String, Object> createErrorResponse(String errorCode, String message,
                                                    String correlationId, HttpStatus status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", errorCode);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", status.value());

        if (correlationId != null) {
            errorResponse.put("correlationId", correlationId);
        }

        return errorResponse;
    }
}