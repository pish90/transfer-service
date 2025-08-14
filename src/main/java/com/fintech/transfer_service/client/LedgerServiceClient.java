package com.fintech.transfer_service.client;

import com.fintech.transfer_service.dto.AccountDto;
import com.fintech.transfer_service.dto.LedgerTransferRequest;
import com.fintech.transfer_service.dto.LedgerTransferResponse;
import com.fintech.transfer_service.dto.TransferResultDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class LedgerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceClient.class);
    private static final String LEDGER_CIRCUIT_BREAKER = "ledgerService";

    private final RestTemplate restTemplate;

    private final String ledgerServiceBaseUrl;

    public LedgerServiceClient(RestTemplate restTemplate,
                               @Value("${services.ledger.base-url:http://localhost:8082}") String ledgerServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.ledgerServiceBaseUrl = ledgerServiceBaseUrl;
    }

    @CircuitBreaker(name = LEDGER_CIRCUIT_BREAKER, fallbackMethod = "getAccountFallback")
    @Retry(name = LEDGER_CIRCUIT_BREAKER)
    public AccountDto getAccount(Long accountId) {
        String correlationId = MDC.get("correlationId");
        log.info("Calling Ledger Service to get account: {} [correlationId={}]", accountId, correlationId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-ID", correlationId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<AccountDto> response = restTemplate.exchange(
                    ledgerServiceBaseUrl + "/accounts/" + accountId,
                    HttpMethod.GET,
                    entity,
                    AccountDto.class
            );

            log.info("Successfully retrieved account: {} [correlationId={}]", accountId, correlationId);
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to retrieve account: {} [correlationId={}]", accountId, correlationId, e);
            throw e;
        }
    }

    @CircuitBreaker(name = LEDGER_CIRCUIT_BREAKER, fallbackMethod = "fallbackTransfer")
    @Retry(name = LEDGER_CIRCUIT_BREAKER)
    public LedgerTransferResponse applyTransfer(Long transferId, Long fromAccountId,
                                                Long toAccountId, BigDecimal amount, String idempotencyKey) {

        String correlationId = MDC.get("correlationId");
        MDC.put("idempotencyKey", idempotencyKey);
        log.info("Calling ledger service for transfer {} [correlationId={}]", transferId, correlationId);

        LedgerTransferRequest request = new LedgerTransferRequest(transferId, fromAccountId, toAccountId, amount);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Correlation-ID", correlationId);
        headers.set("X-Idempotency-Key", idempotencyKey);

        HttpEntity<LedgerTransferRequest> entity = new HttpEntity<>(request, headers);
        String url = ledgerServiceBaseUrl + "/ledger/transfer";

        try {
            ResponseEntity<LedgerTransferResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, LedgerTransferResponse.class);

            LedgerTransferResponse result = response.getBody();
            log.info("Ledger service responded for transfer {} with success={} [correlationId={}]",
                    transferId, result != null && result.isSuccess(), correlationId);

            return result;

        } catch (Exception e) {
            log.error("Error calling ledger service for transfer {} [correlationId={}]. URL: {}. Full error: ",
                    transferId, correlationId, url, e);
            throw e;
        }
    }

    /**
     * Fallback method when circuit breaker is open or retries are exhausted
     */
    public LedgerTransferResponse fallbackTransfer(String transferId, Long fromAccountId,
                                                   Long toAccountId, BigDecimal amount, String idempotencyKey, Throwable ex) {

        String correlationId = MDC.get("correlationId");
        MDC.put("idempotencyKey", idempotencyKey);

        log.error("Fallback triggered for transfer {} [idempotencyKey={}, correlationId={}]. Reason: {}",
                transferId, idempotencyKey, correlationId, ex.getMessage());

        LedgerTransferResponse response = new LedgerTransferResponse();
        response.setTransferId(transferId);
        response.setSuccess(false);
        response.setMessage("Ledger service temporarily unavailable. Please try again later.");

        return response;
    }

    public TransferResultDto transferFunds(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        // Validate accounts exist
        AccountDto fromAccount = getAccount(fromAccountId);
        AccountDto toAccount = getAccount(toAccountId);

        // Check sufficient funds
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        // Simulate ledger operation
        try {
            // Simulate potential failure points
            if (Math.random() < 0.1) { // 10% chance of failure for testing
                throw new RuntimeException("Ledger system error");
            }

            log.info("Transferred {} from account {} to account {}",
                    amount, fromAccountId, toAccountId);

            return new TransferResultDto(true, "Transfer completed");

        } catch (Exception e) {
            throw new RuntimeException("Ledger transfer failed", e);
        }
    }
}
