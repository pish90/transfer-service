package com.fintech.transfer_service.service;

import com.fintech.transfer_service.client.LedgerServiceClient;
import com.fintech.transfer_service.dto.AccountDto;
import com.fintech.transfer_service.dto.TransferResultDto;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    private LedgerServiceClient ledgerServiceClient;
    private final String ledgerServiceUrl = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        ledgerServiceClient = new LedgerServiceClient(restTemplate, ledgerServiceUrl);
        MDC.put("correlationId", "test-correlation-id");
    }

    @Test
    void getAccount_SuccessfulResponse_ReturnsAccount() {
        // Given
        Long accountId = 123456789L;
        AccountDto expectedAccount = new AccountDto(accountId, BigDecimal.valueOf(1000));
        ResponseEntity<AccountDto> response = new ResponseEntity<>(expectedAccount, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/accounts/" + accountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        )).thenReturn(response);

        // When
        AccountDto result = ledgerServiceClient.getAccount(accountId);

        // Then
        assertNotNull(result);
        assertEquals(accountId, result.getId());
        assertEquals(BigDecimal.valueOf(1000), result.getBalance());
    }

    @Test
    void getAccount_ServiceUnavailable_ThrowsException() {
        // Given
        Long accountId = 123456789L;

        when(restTemplate.exchange(
                eq("http://localhost:8081/accounts/" + accountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        )).thenThrow(new ResourceAccessException("Connection refused"));

        // When & Then
        ResourceAccessException exception = assertThrows(
                ResourceAccessException.class,
                () -> ledgerServiceClient.getAccount(accountId)
        );

        assertEquals("Connection refused", exception.getMessage());
    }

    @Test
    void transferFunds_SuccessfulResponse_ReturnsResult() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        BigDecimal amount = BigDecimal.valueOf(100);

        // Mock getAccount calls - both accounts have sufficient funds
        AccountDto fromAccount = new AccountDto(fromAccountId, BigDecimal.valueOf(1000));
        AccountDto toAccount = new AccountDto(toAccountId, BigDecimal.valueOf(500));

        when(restTemplate.exchange(
                eq("http://localhost:8081/accounts/" + fromAccountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        )).thenReturn(new ResponseEntity<>(fromAccount, HttpStatus.OK));

        when(restTemplate.exchange(
                eq("http://localhost:8081/accounts/" + toAccountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        )).thenReturn(new ResponseEntity<>(toAccount, HttpStatus.OK));

        // When
        TransferResultDto result = ledgerServiceClient.transferFunds(fromAccountId, toAccountId, amount);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Transfer completed", result.getMessage());

        // ✅ Only verify GET calls, not POST (since your service doesn't make POST calls)
        verify(restTemplate).exchange(
                eq("http://localhost:8081/accounts/" + fromAccountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        );
        verify(restTemplate).exchange(
                eq("http://localhost:8081/accounts/" + toAccountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        );

        // ✅ Verify POST was never called
        verify(restTemplate, never()).exchange(
                eq("http://localhost:8081/accounts/transfer"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(TransferResultDto.class)
        );
    }

    @Test
    void transferFunds_InsufficientFunds_ThrowsRuntimeException() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        BigDecimal amount = BigDecimal.valueOf(1500); // More than available

        // Mock accounts with insufficient funds
        AccountDto fromAccount = new AccountDto(fromAccountId, BigDecimal.valueOf(1000)); // Less than 1500
        AccountDto toAccount = new AccountDto(toAccountId, BigDecimal.valueOf(500));

        when(restTemplate.exchange(
                eq("http://localhost:8081/accounts/" + fromAccountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        )).thenReturn(new ResponseEntity<>(fromAccount, HttpStatus.OK));

        when(restTemplate.exchange(
                eq("http://localhost:8081/accounts/" + toAccountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        )).thenReturn(new ResponseEntity<>(toAccount, HttpStatus.OK));

        // When & Then
        // ✅ Expect RuntimeException, not IllegalArgumentException
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ledgerServiceClient.transferFunds(fromAccountId, toAccountId, amount)
        );

        // Then
        assertEquals("Insufficient funds", exception.getMessage());

        // ✅ Verify POST was never called (client-side validation failed)
        verify(restTemplate, never()).exchange(
                eq("http://localhost:8081/accounts/transfer"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(TransferResultDto.class)
        );
    }

    @Test
    void transferFunds_ServiceError_ThrowsException() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        BigDecimal amount = BigDecimal.valueOf(100);

        // Mock the first getAccount call to throw exception
        when(restTemplate.exchange(
                eq("http://localhost:8081/accounts/" + fromAccountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        )).thenThrow(new ResourceAccessException("Service timeout"));

        // When & Then
        ResourceAccessException exception = assertThrows(
                ResourceAccessException.class,
                () -> ledgerServiceClient.transferFunds(fromAccountId, toAccountId, amount)
        );

        assertEquals("Service timeout", exception.getMessage());
    }

    @Test
    void transferFunds_AccountNotFound_ThrowsException() {
        // Given
        Long fromAccountId = 999999999L; // Non-existent account
        Long toAccountId = 987654321L;
        BigDecimal amount = BigDecimal.valueOf(100);

        // Mock account not found
        when(restTemplate.exchange(
                eq("http://localhost:8081/accounts/" + fromAccountId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountDto.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Account not found"));

        // When & Then
        HttpClientErrorException exception = assertThrows(
                HttpClientErrorException.class,
                () -> ledgerServiceClient.transferFunds(fromAccountId, toAccountId, amount)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
