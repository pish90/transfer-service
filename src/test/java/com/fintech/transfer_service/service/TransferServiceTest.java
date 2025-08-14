package com.fintech.transfer_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.transfer_service.client.LedgerServiceClient;
import com.fintech.transfer_service.data.TransferStatus;
import com.fintech.transfer_service.dto.AccountDto;
import com.fintech.transfer_service.dto.CreateTransferRequestDto;
import com.fintech.transfer_service.dto.LedgerTransferResponse;
import com.fintech.transfer_service.dto.TransferDto;
import com.fintech.transfer_service.dto.TransferResultDto;
import com.fintech.transfer_service.entity.IdempotencyRecord;
import com.fintech.transfer_service.entity.Transfer;
import com.fintech.transfer_service.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerServiceClient ledgerServiceClient;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Executor transferExecutor;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransferService transferService;

    private CreateTransferRequestDto validRequest;
    private AccountDto fromAccount;
    private AccountDto toAccount;
    private String defaultIdempotencyKey;

    @BeforeEach
    void setUp() {
        MDC.put("correlationId", "test-correlation-id");

        defaultIdempotencyKey = "test-idempotency-key-" + System.currentTimeMillis();

        validRequest = new CreateTransferRequestDto(
                123456789L, 987654321L, BigDecimal.valueOf(100), defaultIdempotencyKey
        );

        fromAccount = new AccountDto(123456789L, BigDecimal.valueOf(500));
        toAccount = new AccountDto(987654321L, BigDecimal.valueOf(200));
    }

    @Test
    void createTransfer_ValidRequest_ReturnsCompletedTransfer() throws JsonProcessingException {
        // Given
        Transfer savedTransfer = createTestTransfer();
        savedTransfer.setStatus(TransferStatus.COMPLETED);

        TransferDto expectedDto = mapToDto(savedTransfer);
        String responseJson = "{\"id\":\"transfer-123\",\"status\":\"COMPLETED\"}";

        when(idempotencyService.findExistingRecord(defaultIdempotencyKey)).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(savedTransfer);
        when(ledgerServiceClient.transferFunds(123456789L, 987654321L, BigDecimal.valueOf(100)))
                .thenReturn(new TransferResultDto(true, "Transfer completed"));
        when(objectMapper.writeValueAsString(any(TransferDto.class))).thenReturn(responseJson);

        // When
        TransferDto result = transferService.createTransfer(validRequest, defaultIdempotencyKey);

        // Then
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        assertEquals(BigDecimal.valueOf(100), result.getAmount());
        assertEquals(123456789L, result.getFromAccountId());
        assertEquals(987654321L, result.getToAccountId());

        verify(transferRepository, times(2)).save(any(Transfer.class)); // PENDING then COMPLETED
        verify(ledgerServiceClient).transferFunds(123456789L, 987654321L, BigDecimal.valueOf(100));
        verify(idempotencyService).saveIdempotencyRecord(eq(defaultIdempotencyKey), any(Transfer.class), eq(200));
    }

    @Test
    void createTransfer_IdempotentRequest_ReturnsCachedResponse() throws JsonProcessingException {
        // Given
        String idempotencyKey = "test-idempotency-key";
        String cachedResponseJson = "{\"id\":\"123456L\",\"status\":\"COMPLETED\"}";

        IdempotencyRecord existingRecord = new IdempotencyRecord(
                idempotencyKey, 123456L,
                cachedResponseJson, 200
        );

        TransferDto expectedDto = new TransferDto();
        expectedDto.setId(123456L);
        expectedDto.setStatus(TransferStatus.COMPLETED);

        when(idempotencyService.findExistingRecord(idempotencyKey))
                .thenReturn(Optional.of(existingRecord));
        when(objectMapper.readValue(cachedResponseJson, TransferDto.class))
                .thenReturn(expectedDto);

        // When
        TransferDto result = transferService.createTransfer(validRequest, idempotencyKey);

        // Then
        assertNotNull(result);
        assertEquals(123456L, result.getId());
        assertEquals(TransferStatus.COMPLETED, result.getStatus());

        verify(transferRepository, never()).save(any());
        verify(ledgerServiceClient, never()).transferFunds(any(), any(), any());
        verify(objectMapper).readValue(cachedResponseJson, TransferDto.class);
    }

    // Test idempotency validation (these should come first since createTransfer() validates idempotency first)
    @Test
    void createTransfer_NullIdempotencyKey_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.createTransfer(validRequest, null)
        );

        assertEquals("Idempotency key must be provided", exception.getMessage());
        verify(transferRepository, never()).save(any());
        verify(ledgerServiceClient, never()).transferFunds(any(), any(), any());
    }

    @Test
    void createTransfer_EmptyIdempotencyKey_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.createTransfer(validRequest, "")
        );

        assertEquals("Idempotency key must be provided", exception.getMessage());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_BlankIdempotencyKey_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.createTransfer(validRequest, "   ")
        );

        assertEquals("Idempotency key must be provided", exception.getMessage());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_LedgerServiceFails_ThrowsException() throws JsonProcessingException {
        // Given
        Transfer savedTransfer = createTestTransfer();

        when(idempotencyService.findExistingRecord(defaultIdempotencyKey)).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(savedTransfer);
        when(ledgerServiceClient.transferFunds(123456789L, 987654321L, BigDecimal.valueOf(100)))
                .thenThrow(new RuntimeException("Ledger service failed"));

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> transferService.createTransfer(validRequest, defaultIdempotencyKey)
        );

        assertEquals("Ledger service failed", exception.getMessage());
        verify(transferRepository, times(1)).save(any(Transfer.class)); // Only saves PENDING state
        verify(idempotencyService, never()).saveIdempotencyRecord(any(), any(), anyInt());
    }

    // Tests for the initiateTransfer method (which has business validation first)
    @Test
    void initiateTransfer_SameAccountTransfer_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.initiateTransfer(123456789L, 123456789L, BigDecimal.valueOf(100), defaultIdempotencyKey)
        );

        // This method would need to be updated in your service to check same account
        assertTrue(exception.getMessage().contains("account"));
        verify(transferRepository, never()).save(any());
    }

    @Test
    void initiateTransfer_ZeroAmount_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.initiateTransfer(123456789L, 987654321L, BigDecimal.ZERO, defaultIdempotencyKey)
        );

        assertEquals("Transfer amount must be positive", exception.getMessage());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void initiateTransfer_NegativeAmount_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.initiateTransfer(123456789L, 987654321L, BigDecimal.valueOf(-50), defaultIdempotencyKey)
        );

        assertEquals("Transfer amount must be positive", exception.getMessage());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void initiateTransfer_NullIdempotencyKey_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.initiateTransfer(123456789L, 987654321L, BigDecimal.valueOf(100), null)
        );

        assertEquals("Idempotency key is required", exception.getMessage());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void initiateTransfer_ValidRequest_ReturnsCompletedTransfer() {
        // Given
        Transfer savedTransfer = createTestTransfer();
        savedTransfer.setStatus(TransferStatus.COMPLETED);

        LedgerTransferResponse successResponse = new LedgerTransferResponse(true, "Success");

        when(transferRepository.findByIdempotencyKey(defaultIdempotencyKey)).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(savedTransfer);
        when(ledgerServiceClient.applyTransfer(anyLong(), eq(123456789L), eq(987654321L), eq(BigDecimal.valueOf(100)), eq(defaultIdempotencyKey)))
                .thenReturn(successResponse);

        // When
        Transfer result = transferService.initiateTransfer(123456789L, 987654321L, BigDecimal.valueOf(100), defaultIdempotencyKey);

        // Then
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        verify(transferRepository, times(2)).save(any(Transfer.class));
        verify(ledgerServiceClient).applyTransfer(anyLong(), eq(123456789L), eq(987654321L), eq(BigDecimal.valueOf(100)), eq(defaultIdempotencyKey));
    }

    @Test
    void processBatchTransfers_ExceedsLimit_ThrowsException() {
        // Given
        List<CreateTransferRequestDto> largeBatch = IntStream.range(0, 21)
                .mapToObj(i -> new CreateTransferRequestDto(123L + i, 456L + i, BigDecimal.TEN, defaultIdempotencyKey))
                .collect(Collectors.toList());

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.processBatchTransfers(largeBatch)
        );

        assertEquals("Batch size cannot exceed 20 transfers", exception.getMessage());
    }

    private Transfer createTestTransfer() {
        Transfer transfer = new Transfer();
        transfer.setId(123456L);
        transfer.setIdempotencyKey(defaultIdempotencyKey);
        transfer.setFromAccountId(123456789L);
        transfer.setToAccountId(987654321L);
        transfer.setAmount(BigDecimal.valueOf(100));
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setCreatedAt(LocalDateTime.now());
        return transfer;
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