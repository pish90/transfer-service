package com.fintech.transfer_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.transfer_service.client.LedgerServiceClient;
import com.fintech.transfer_service.data.TransferStatus;
import com.fintech.transfer_service.dto.AccountDto;
import com.fintech.transfer_service.dto.CreateTransferRequestDto;
import com.fintech.transfer_service.dto.TransferDto;
import com.fintech.transfer_service.dto.TransferResultDto;
import com.fintech.transfer_service.entity.IdempotencyRecord;
import com.fintech.transfer_service.entity.Transfer;
import com.fintech.transfer_service.exception.LedgerServiceUnavailableException;
import com.fintech.transfer_service.exception.TransferException;
import com.fintech.transfer_service.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        MDC.put("correlationId", "test-correlation-id");

        validRequest = new CreateTransferRequestDto(
                123456789L, 987654321L, BigDecimal.valueOf(100)
        );

        fromAccount = new AccountDto(123456789L, BigDecimal.valueOf(500));
        toAccount = new AccountDto(987654321L, BigDecimal.valueOf(200));
    }

    @Test
    void createTransfer_ValidRequest_ReturnsCompletedTransfer() throws JsonProcessingException {
        // Given
        Transfer savedTransfer = createTestTransfer();
        savedTransfer.setStatus(TransferStatus.COMPLETED);

        TransferResultDto transferResult = new TransferResultDto(true, "Transfer completed");

        when(idempotencyService.findExistingRecord(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(savedTransfer);
        when(ledgerServiceClient.getAccount(123456789L)).thenReturn(fromAccount);
        when(ledgerServiceClient.getAccount(987654321L)).thenReturn(toAccount);
        when(ledgerServiceClient.transferFunds(123456789L, 987654321L, BigDecimal.valueOf(100)))
                .thenReturn(transferResult);

        // When
        TransferDto result = transferService.createTransfer(validRequest, null);

        // Then
        assertNotNull(result);
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        assertEquals(BigDecimal.valueOf(100), result.getAmount());
        assertEquals(123456789L, result.getFromAccountId());
        assertEquals(987654321L, result.getToAccountId());

        verify(transferRepository, times(2)).save(any(Transfer.class)); // PENDING then COMPLETED
        verify(ledgerServiceClient).transferFunds(123456789L, 987654321L, BigDecimal.valueOf(100));
    }

    @Test
    void createTransfer_IdempotentRequest_ReturnsCachedResponse() throws JsonProcessingException {
        // Given
        String idempotencyKey = "test-idempotency-key";
        String cachedResponseJson = "{\"id\":\"transfer-123\",\"status\":\"COMPLETED\"}";

        IdempotencyRecord existingRecord = new IdempotencyRecord(
                idempotencyKey, "transfer-123",
                cachedResponseJson, 200
        );

        // Create the expected DTO that ObjectMapper should return
        TransferDto expectedDto = new TransferDto();
        expectedDto.setId("transfer-123");
        expectedDto.setStatus(TransferStatus.COMPLETED);

        when(idempotencyService.findExistingRecord(idempotencyKey))
                .thenReturn(Optional.of(existingRecord));
        when(objectMapper.readValue(cachedResponseJson, TransferDto.class))
                .thenReturn(expectedDto);

        // When
        TransferDto result = transferService.createTransfer(validRequest, idempotencyKey);

        // Then
        assertNotNull(result);
        assertEquals("transfer-123", result.getId());
        assertEquals(TransferStatus.COMPLETED, result.getStatus());

        // Should not create new transfer or call ledger service
        verify(transferRepository, never()).save(any());
        verify(ledgerServiceClient, never()).transferFunds(any(), any(), any());
        verify(objectMapper).readValue(cachedResponseJson, TransferDto.class);
    }

    @Test
    void createTransfer_InsufficientFunds_ThrowsException() {
        // Given
        AccountDto poorAccount = new AccountDto(123456789L, BigDecimal.valueOf(50)); // Less than transfer amount

        when(idempotencyService.findExistingRecord(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(createTestTransfer());
        when(ledgerServiceClient.getAccount(123456789L)).thenReturn(poorAccount);
        when(ledgerServiceClient.getAccount(987654321L)).thenReturn(toAccount);

        // When & Then
        TransferException exception = assertThrows(
                TransferException.class,
                () -> transferService.createTransfer(validRequest, null)
        );

        assertTrue(exception.getMessage().contains("Insufficient funds"));
        verify(ledgerServiceClient, never()).transferFunds(any(), any(), any());
    }

    @Test
    void createTransfer_LedgerServiceUnavailable_FailsGracefully() {
        // Given
        Transfer savedTransfer = createTestTransfer();

        when(idempotencyService.findExistingRecord(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(savedTransfer);
        when(ledgerServiceClient.getAccount(123456789L))
                .thenThrow(new LedgerServiceUnavailableException("Service unavailable", new RuntimeException()));

        // When & Then
        TransferException exception = assertThrows(
                TransferException.class,
                () -> transferService.createTransfer(validRequest, null)
        );

        assertTrue(exception.getMessage().contains("Ledger service unavailable"));
        verify(transferRepository, times(2)).save(any(Transfer.class)); // PENDING then FAILED
    }

    @Test
    void createTransfer_SameAccountTransfer_ThrowsException() {
        // Given
        CreateTransferRequestDto sameAccountRequest = new CreateTransferRequestDto(
                123456789L, 123456789L, BigDecimal.valueOf(100)
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.createTransfer(sameAccountRequest, null)
        );

        assertEquals("Cannot transfer to the same account", exception.getMessage());
        verify(transferRepository, never()).save(any());
        verify(ledgerServiceClient, never()).getAccount(any());
    }

    @Test
    void createTransfer_ZeroAmount_ThrowsException() {
        // Given
        CreateTransferRequestDto zeroAmountRequest = new CreateTransferRequestDto(
                123456789L, 987654321L, BigDecimal.ZERO
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.createTransfer(zeroAmountRequest, null)
        );

        assertEquals("Transfer amount must be positive", exception.getMessage());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_NegativeAmount_ThrowsException() {
        // Given
        CreateTransferRequestDto negativeAmountRequest = new CreateTransferRequestDto(
                123456789L, 987654321L, BigDecimal.valueOf(-50)
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.createTransfer(negativeAmountRequest, null)
        );

        assertEquals("Transfer amount must be positive", exception.getMessage());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_FromAccountNotFound_ThrowsException() {
        // Given
        when(idempotencyService.findExistingRecord(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(createTestTransfer());
        when(ledgerServiceClient.getAccount(123456789L))
                .thenThrow(new IllegalArgumentException("Account not found: from-account"));

        // When & Then
        TransferException exception = assertThrows(
                TransferException.class,
                () -> transferService.createTransfer(validRequest, null)
        );

        assertTrue(exception.getMessage().contains("Account not found"));
        verify(transferRepository, times(2)).save(any(Transfer.class)); // PENDING then FAILED
    }

    @Test
    void createTransfer_ToAccountNotFound_ThrowsException() {
        // Given
        when(idempotencyService.findExistingRecord(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(createTestTransfer());
        when(ledgerServiceClient.getAccount(123456789L)).thenReturn(fromAccount);
        when(ledgerServiceClient.getAccount(987654321L))
                .thenThrow(new IllegalArgumentException("Account not found: to-account"));

        // When & Then
        TransferException exception = assertThrows(
                TransferException.class,
                () -> transferService.createTransfer(validRequest, null)
        );

        assertTrue(exception.getMessage().contains("Account not found"));
        verify(transferRepository, times(2)).save(any(Transfer.class)); // PENDING then FAILED
    }

    @Test
    void createTransfer_LedgerTransferFails_MarksTransferAsFailed() {
        // Given
        Transfer savedTransfer = createTestTransfer();

        when(idempotencyService.findExistingRecord(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class))).thenReturn(savedTransfer);
        when(ledgerServiceClient.getAccount(123456789L)).thenReturn(fromAccount);
        when(ledgerServiceClient.getAccount(987654321L)).thenReturn(toAccount);
        when(ledgerServiceClient.transferFunds(123456789L, 987654321L, BigDecimal.valueOf(100)))
                .thenThrow(new RuntimeException("Ledger transfer failed"));

        // When & Then
        TransferException exception = assertThrows(
                TransferException.class,
                () -> transferService.createTransfer(validRequest, null)
        );

        assertTrue(exception.getMessage().contains("Transfer failed"));
        verify(transferRepository, times(2)).save(any(Transfer.class)); // PENDING then FAILED
    }

    private Transfer createTestTransfer() {
        return new Transfer(
                "transfer-123",
                "test-key",
                123456789L,
                987654321L,
                BigDecimal.valueOf(100),
                TransferStatus.COMPLETED
        );
    }
}