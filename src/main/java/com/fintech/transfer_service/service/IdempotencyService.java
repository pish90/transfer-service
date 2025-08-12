package com.fintech.transfer_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.transfer_service.entity.IdempotencyRecord;
import com.fintech.transfer_service.entity.Transfer;
import com.fintech.transfer_service.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public Optional<IdempotencyRecord> findExistingRecord(String idempotencyKey) {
        return idempotencyRepository.findByIdempotencyKeyAndExpiresAtAfter(
                idempotencyKey, LocalDateTime.now());
    }

    public void saveIdempotencyRecord(String idempotencyKey, Transfer transfer, int httpStatus) {
        try {
            String responseBody = objectMapper.writeValueAsString(transfer);
            IdempotencyRecord record = new IdempotencyRecord(
                    idempotencyKey, transfer.getId(), responseBody, httpStatus);
            idempotencyRepository.save(record);
        } catch (Exception e) {
            // Log error but don't fail the transfer
            log.error("Failed to save idempotency record for key: {}", idempotencyKey, e);
        }
    }

    @Scheduled(fixedRate = 3600000) // Clean up every hour
    public void cleanupExpiredRecords() {
        int deleted = idempotencyRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned up {} expired idempotency records", deleted);
    }
}
