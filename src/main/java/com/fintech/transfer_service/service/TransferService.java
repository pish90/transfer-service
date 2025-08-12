package com.fintech.transfer_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.transfer_service.client.LedgerServiceClient;
import com.fintech.transfer_service.data.TransferStatus;
import com.fintech.transfer_service.dto.CreateTransferRequestDto;
import com.fintech.transfer_service.dto.LedgerTransferResponse;
import com.fintech.transfer_service.dto.TransferDto;
import com.fintech.transfer_service.entity.IdempotencyRecord;
import com.fintech.transfer_service.entity.Transfer;
import com.fintech.transfer_service.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final LedgerServiceClient ledgerServiceClient;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;

    /**
     * Initiate a single transfer
     */
    @Transactional
    public Transfer initiateTransfer(Long fromAccountId, Long toAccountId,
                                     BigDecimal amount, String idempotencyKey) {

        log.info("Initiating transfer from {} to {} amount {} [idempotencyKey={}]",
                fromAccountId, toAccountId, amount, idempotencyKey);

        // Check for idempotency
        Optional<Transfer> existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTransfer.isPresent()) {
            log.info("Transfer already exists for idempotency key {}, returning existing transfer", idempotencyKey);
            return existingTransfer.get();
        }

        // Validate inputs
        validateTransferRequest(fromAccountId, toAccountId, amount, idempotencyKey);

        // Create transfer record
        String transferId = UUID.randomUUID().toString();
        Transfer transfer = new Transfer(transferId, idempotencyKey, fromAccountId, toAccountId, amount);
        transfer = transferRepository.save(transfer);

        try {
            // Call ledger service
            LedgerTransferResponse ledgerResponse = ledgerServiceClient.applyTransfer(
                    transferId, fromAccountId, toAccountId, amount);

            // Update transfer status based on response
            if (ledgerResponse.isSuccess()) {
                transfer.markCompleted(ledgerResponse.getMessage());
                log.info("Transfer {} completed successfully", transferId);
            } else {
                transfer.markFailed(ledgerResponse.getMessage());
                log.warn("Transfer {} failed: {}", transferId, ledgerResponse.getMessage());
            }

        } catch (Exception e) {
            transfer.markFailed("Service unavailable: " + e.getMessage());
            log.error("Transfer {} failed due to service error", transferId, e);
        }

        return transferRepository.save(transfer);
    }

    /**
     * Process multiple transfers concurrently
     */
    public List<Transfer> processBatchTransfers(List<CreateTransferRequestDto> requests) {
        log.info("Processing batch of {} transfers", requests.size());

        if (requests.size() > 20) {
            throw new IllegalArgumentException("Batch size cannot exceed 20 transfers");
        }

        // Process transfers concurrently
        List<CompletableFuture<Transfer>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() ->
                        initiateTransfer(
                                request.getFromAccountId(),
                                request.getToAccountId(),
                                request.getAmount(),
                                request.getIdempotencyKey()
                        ), taskExecutor))
                .toList();

        // Wait for all to complete and collect results
        List<Transfer> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        log.info("Batch processing completed. {} transfers processed", results.size());
        return results;
    }

    /**
     * Get transfer by ID
     */
    @Transactional(readOnly = true)
    public Optional<Transfer> getTransfer(String transferId) {
        return transferRepository.findById(Long.valueOf(transferId));
    }

    private void validateTransferRequest(Long fromAccountId, Long toAccountId,
                                         BigDecimal amount, String idempotencyKey) {
        if (fromAccountId <= 0) {
            throw new IllegalArgumentException("From account ID is required");
        }
        if (toAccountId <= 0) {
            throw new IllegalArgumentException("To account ID is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
    }

    public TransferDto createTransfer(CreateTransferRequestDto validRequest, String idempotencyKey) throws JsonProcessingException {
        // Check for existing idempotent request
        Optional<IdempotencyRecord> existingRecord = idempotencyService.findExistingRecord(idempotencyKey);

        if (existingRecord.isPresent()) {
            // Return cached response for idempotent request
            String cachedResponse = existingRecord.get().getResponseBody();
            return objectMapper.readValue(cachedResponse, TransferDto.class);
        }

        // Create new transfer
        Transfer transfer = new Transfer();
        transfer.setId(generateTransferId());
        transfer.setFromAccountId(validRequest.getFromAccountId());
        transfer.setToAccountId(validRequest.getToAccountId());
        transfer.setAmount(validRequest.getAmount());
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setCreatedAt(LocalDateTime.now());

        // Save transfer
        Transfer savedTransfer = transferRepository.save(transfer);

        // Call ledger service
        ledgerServiceClient.transferFunds(
                validRequest.getFromAccountId(),
                validRequest.getToAccountId(),
                validRequest.getAmount()
        );

        // Update status after successful ledger call
        savedTransfer.setStatus(TransferStatus.COMPLETED);
        transferRepository.save(savedTransfer);

        // Convert to DTO
        TransferDto responseDto = mapToDto(savedTransfer);

        // Store idempotency record
        String responseBody = objectMapper.writeValueAsString(responseDto);
        idempotencyService.saveIdempotencyRecord(idempotencyKey, savedTransfer, 200);

        return responseDto;
    }

    private String generateTransferId() {
        return "txn-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private TransferDto mapToDto(Transfer transfer) {
        TransferDto dto = new TransferDto();
        dto.setId(transfer.getId());
        dto.setFromAccountId(transfer.getFromAccountId());
        dto.setToAccountId(transfer.getToAccountId());
        dto.setAmount(transfer.getAmount());
        dto.setStatus(transfer.getStatus());
        dto.setCreatedAt(transfer.getCreatedAt());
        dto.setCompletedAt(transfer.getCompletedAt());
        return dto;
    }
}
