package com.fintech.transfer_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    private String idempotencyKey;

    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public IdempotencyRecord(String idempotencyKey, Long transferId,
                             String responseBody, Integer httpStatus) {
        this.idempotencyKey = idempotencyKey;
        this.transferId = transferId;
        this.responseBody = responseBody;
        this.httpStatus = httpStatus;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusHours(24); // 24 hour TTL
    }
}
