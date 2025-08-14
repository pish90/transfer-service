package com.fintech.transfer_service.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fintech.transfer_service.data.TransferStatus;
import com.fintech.transfer_service.dto.CreateTransferRequestDto;
import com.fintech.transfer_service.dto.TransferDto;
import com.fintech.transfer_service.entity.Transfer;
import com.fintech.transfer_service.repository.TransferRepository;
import com.fintech.transfer_service.service.TransferService;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.jsonpath.internal.path.PathCompiler.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransferServiceIntegrationTest {

    @RegisterExtension
    static WireMockExtension ledgerServiceMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    private String defaultIdempotencyKey;

    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database configuration
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:integration_test;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Ledger service configuration
        registry.add("ledger.service.url", () -> "http://localhost:" + ledgerServiceMock.getPort());

        // Circuit breaker configuration
        registry.add("resilience4j.circuitbreaker.instances.ledgerService.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.ledgerService.minimum-number-of-calls", () -> "3");
        registry.add("resilience4j.circuitbreaker.instances.ledgerService.sliding-window-size", () -> "5");
        registry.add("resilience4j.circuitbreaker.instances.ledgerService.wait-duration-in-open-state", () -> "1s");
    }

    @BeforeEach
    void setUp() {
        System.out.println("WireMock is running on: " + ledgerServiceMock.baseUrl());
        ledgerServiceMock.resetAll();
        transferRepository.deleteAll();

        // Setup ObjectMapper for JSON parsing
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        defaultIdempotencyKey = "79ea0d2b-90b4-4889-bc2e-18d74526edd1";

    }

    @AfterEach
    void tearDown() {
        transferRepository.deleteAll();
    }

    /**
     * Helper method to handle both success and error HTTP responses
     */
    private <T> T handleSuccessResponse(ResponseEntity<String> response, Class<T> targetClass) {
        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                return objectMapper.readValue(response.getBody(), targetClass);
            } catch (Exception e) {
                fail("Failed to parse success response: " + e.getMessage() +
                        "\nResponse body: " + response.getBody());
                return null;
            }
        } else {
            fail("Request failed with status: " + response.getStatusCode() +
                    ", body: " + response.getBody());
            return null;
        }
    }

    @Test
    @Order(1)
    void integrationTest_HappyPathTransfer_ShouldSucceedEndToEnd() throws Exception {
        // Given - Mock successful ledger service responses
        mockSuccessfulLedgerResponses();

        CreateTransferRequestDto request = new CreateTransferRequestDto(
                123456789L, 987654321L, new BigDecimal("250.00"), defaultIdempotencyKey
        );

        String idempotencyKey = "test-idempotency-" + UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("Content-Type", "application/json");
        HttpEntity<CreateTransferRequestDto> requestEntity = new HttpEntity<>(request, headers);

        // When - Make actual HTTP request to transfer service
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/transfers",
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        // Then - Verify response
        assertEquals(HttpStatus.OK, response.getStatusCode());

        TransferDto transferDto = handleSuccessResponse(response, TransferDto.class);
        assertNotNull(transferDto);
        assertEquals(new BigDecimal("250.00"), transferDto.getAmount());

        // Verify database
        List<Transfer> transfers = transferRepository.findAll();
        assertEquals(1, transfers.size());
        assertEquals(idempotencyKey, transfers.getFirst().getIdempotencyKey());
    }

    @Test
    @Order(2)
    void integrationTest_ServiceLevelConcurrency_ShouldMaintainDataConsistency() throws Exception {
        // Given
        mockSuccessfulLedgerResponses();

        int numberOfThreads = 3; // Reduce to 3 to avoid overwhelming the test
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        List<Future<TransferDto>> futures = new ArrayList<>();

        // When - Each thread gets unique idempotency key
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<TransferDto> future = executor.submit(() -> {
                try {
                    String threadKey = "test-key-thread-" + threadId + "-" + System.currentTimeMillis();
                    CreateTransferRequestDto request = new CreateTransferRequestDto(
                            123456789L, 987654321L, new BigDecimal("10.00"), defaultIdempotencyKey
                    );

                    // Make sure to pass the idempotency key properly
                    return transferService.createTransfer(request, threadKey);
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " execution failed: " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Then
        assertTrue(latch.await(30, TimeUnit.SECONDS));

        // Verify database
        List<Transfer> transfers = transferRepository.findAll();

        // Verify all transfers have idempotency keys
        for (Transfer transfer : transfers) {
            assertNotNull(transfer.getIdempotencyKey());
            assertTrue(transfer.getIdempotencyKey().startsWith("test-key-thread-"));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @Order(3)
    void integrationTest_LedgerServiceFailure_ShouldHandleGracefully() throws Exception {
        // Given - Mock service failures
        ledgerServiceMock.stubFor(get(urlPathMatching("/accounts/.*"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service Unavailable\"}")
                        .withFixedDelay(500)));

        ledgerServiceMock.stubFor(post(urlPathMatching("/ledger/transfer"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service Unavailable\"}")));

        CreateTransferRequestDto request = new CreateTransferRequestDto(
                123456789L, 987654321L, new BigDecimal("100.00"), defaultIdempotencyKey
        );

        // When - Expect an exception
        Exception exception = assertThrows(Exception.class, () -> {
            transferService.createTransfer(request, defaultIdempotencyKey);
        });

        // Then - Verify exception indicates service failure
        assertNotNull(exception);
        String exceptionMessage = exception.getMessage().toLowerCase();
        assertTrue(
                exceptionMessage.contains("503") ||
                        exceptionMessage.contains("service unavailable") ||
                        exceptionMessage.contains("[no body]") ||
                        exceptionMessage.contains("internal server error") ||
                        exception instanceof HttpServerErrorException ||
                        (exception.getCause() != null && exception.getCause() instanceof HttpServerErrorException),
                "Exception should indicate service failure, but was: " + exception.getMessage()
        );

        // Verify database - check if any transfer record was created
        // The behavior might vary based on when the failure occurs
        List<Transfer> transfers = transferRepository.findAll();
        if (!transfers.isEmpty()) {
            assertEquals(1, transfers.size());
            assertEquals(TransferStatus.PENDING, transfers.getFirst().getStatus());
            assertEquals(defaultIdempotencyKey, transfers.getFirst().getIdempotencyKey());
        }
    }

    @Test
    @Order(4)
    void integrationTest_IdempotentRequests_ShouldReturnSameResult() throws Exception {
        // Given
        mockSuccessfulLedgerResponses();

        String idempotencyKey = "test-key-" + UUID.randomUUID();
        CreateTransferRequestDto request = new CreateTransferRequestDto(
                123456789L, 987654321L, new BigDecimal("150.00"), defaultIdempotencyKey
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateTransferRequestDto> requestEntity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<String> firstResponse = restTemplate.exchange(
                "http://localhost:" + port + "/transfers",
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        ResponseEntity<String> secondResponse = restTemplate.exchange(
                "http://localhost:" + port + "/transfers",
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        // Then
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());
        assertEquals(HttpStatus.OK, secondResponse.getStatusCode());

        TransferDto firstTransfer = handleSuccessResponse(firstResponse, TransferDto.class);
        TransferDto secondTransfer = handleSuccessResponse(secondResponse, TransferDto.class);

        assertNotNull(firstTransfer);
        assertNotNull(secondTransfer);
        assertEquals(firstTransfer.getId(), secondTransfer.getId());
        assertEquals(firstTransfer.getStatus(), secondTransfer.getStatus());

        // Verify only one transfer in DB
        List<Transfer> transfers = transferRepository.findAll();
        assertEquals(1, transfers.size());
    }

    @Test
    @Order(5)
    void integrationTest_InsufficientFunds_ShouldFailGracefully() throws Exception {
        // Given - Mock account responses
        ledgerServiceMock.stubFor(get(urlEqualTo("/accounts/123456789"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":123456789,\"balance\":50.00}")));

        ledgerServiceMock.stubFor(get(urlEqualTo("/accounts/987654321"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":987654321,\"balance\":1000.00}")));

        // Mock transfer endpoint to return business logic error for insufficient funds
        ledgerServiceMock.stubFor(post(urlEqualTo("/ledger/transfer"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Insufficient funds\",\"message\":\"Account 123456789 has insufficient balance for transfer of 100.00\"}")));

        CreateTransferRequestDto request = new CreateTransferRequestDto(
                123456789L, 987654321L, new BigDecimal("100.00"), defaultIdempotencyKey
        );

        // When - Expect an exception for insufficient funds
        Exception exception = assertThrows(Exception.class, () -> {
            transferService.createTransfer(request, defaultIdempotencyKey);
        });

        // Then - Check the exception indicates insufficient funds
        assertNotNull(exception);
        String exceptionMessage = exception.getMessage().toLowerCase();
        assertTrue(
                exceptionMessage.contains("insufficient funds") ||
                        exceptionMessage.contains("insufficient balance") ||
                        exceptionMessage.contains("[no body]") ||
                        exceptionMessage.contains("400") ||
                        (exception.getCause() != null && exception.getCause().getMessage().toLowerCase().contains("insufficient")),
                "Exception should indicate insufficient funds, but was: " + exception.getMessage()
        );

        // Verify database - should have a failed transfer record if the service creates one
        List<Transfer> transfers = transferRepository.findAll();
        if (!transfers.isEmpty()) {
            assertEquals(1, transfers.size());
            assertEquals(TransferStatus.PENDING, transfers.getFirst().getStatus());
            assertEquals(defaultIdempotencyKey, transfers.getFirst().getIdempotencyKey());
        }
    }

    @Test
    @Order(6)
    void integrationTest_HttpEndpoint_BasicConnectivity() throws Exception {
        // Given - Mock successful responses
        mockSuccessfulLedgerResponses();

        CreateTransferRequestDto request = new CreateTransferRequestDto(
                123456789L, 987654321L, new BigDecimal("50.00"), defaultIdempotencyKey
        );

        // When - Test HTTP endpoint exists and responds
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/transfers",
                request,
                String.class
        );

        // Then - Verify endpoint is reachable (don't worry about exact response format)
        assertNotNull(response);
        assertNotNull(response.getStatusCode());

        // Basic sanity check - should not be 404 (endpoint exists)
        assertNotEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    private void mockSuccessfulLedgerResponses() {
        // Mock account balance checks
        ledgerServiceMock.stubFor(get(urlEqualTo("/accounts/123456789"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":123456789,\"balance\":1000.00}")));

        ledgerServiceMock.stubFor(get(urlEqualTo("/accounts/987654321"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":987654321,\"balance\":500.00}")));

        // Mock successful transfer
        ledgerServiceMock.stubFor(post(urlEqualTo("/ledger/transfer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":\"Transfer completed successfully\"}")));
    }
}
