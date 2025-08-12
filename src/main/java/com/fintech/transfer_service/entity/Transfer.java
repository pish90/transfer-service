package com.fintech.transfer_service.entity;

import com.fintech.transfer_service.data.TransferStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "transfers",
        indexes = {
                @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
                @Index(name = "idx_created_at", columnList = "createdAt")
        })
public class Transfer {

    @Id
    private String id;

    @NotNull
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @NotNull
    @Column(name = "from_account_id")
    private Long fromAccountId;

    @NotNull
    @Column(name = "to_account_id")
    private Long toAccountId;

    @NotNull
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    private String message;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Transfer(String id, String idempotencyKey, Long fromAccountId,
                    Long toAccountId, BigDecimal amount) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.status = TransferStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Transfer(String id, String idempotencyKey, Long fromAccountId, Long toAccountId, BigDecimal amount, TransferStatus status) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.status = status;
    }

    public void markCompleted(String message) {
        this.status = TransferStatus.COMPLETED;
        this.message = message;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = TransferStatus.FAILED;
        this.message = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }
}