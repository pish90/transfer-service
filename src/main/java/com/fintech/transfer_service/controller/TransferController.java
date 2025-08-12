package com.fintech.transfer_service.controller;

import com.fintech.transfer_service.controller.dto.BatchTransferResponse;
import com.fintech.transfer_service.controller.dto.TransferRequest;
import com.fintech.transfer_service.controller.dto.TransferResponse;
import com.fintech.transfer_service.dto.BatchTransferRequestDto;
import com.fintech.transfer_service.dto.CreateTransferRequestDto;
import com.fintech.transfer_service.entity.Transfer;
import com.fintech.transfer_service.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/transfers")
@Tag(name = "Transfer Management", description = "Transfer initiation and status tracking")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;

    @PostMapping
    @Operation(summary = "Initiate a transfer", description = "Create a new transfer between accounts")
    @ApiResponse(responseCode = "201", description = "Transfer initiated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid transfer request")
    public ResponseEntity<TransferResponse> initiateTransfer(
            @Valid @RequestBody TransferRequest request,
            @Parameter(description = "Idempotency key for duplicate prevention", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        // Set correlation ID for tracing
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        try {
            log.info("Received transfer request from {} to {} amount {} [correlationId={}]",
                    request.getFromAccountId(), request.getToAccountId(), request.getAmount(), correlationId);

            Transfer transfer = transferService.initiateTransfer(
                    request.getFromAccountId(),
                    request.getToAccountId(),
                    request.getAmount(),
                    idempotencyKey
            );

            TransferResponse response = TransferResponse.fromTransfer(transfer);
            response.setCorrelationId(correlationId);

            HttpStatus status = HttpStatus.OK; // OK for idempotent requests

            return ResponseEntity.status(status).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid transfer request [correlationId={}]: {}", correlationId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error processing transfer [correlationId={}]", correlationId, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("correlationId");
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transfer status", description = "Retrieve transfer details by ID")
    @ApiResponse(responseCode = "200", description = "Transfer found")
    @ApiResponse(responseCode = "404", description = "Transfer not found")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable String id) {
        Optional<Transfer> transfer = transferService.getTransfer(id);

        if (transfer.isPresent()) {
            TransferResponse response = TransferResponse.fromTransfer(transfer.get());
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/batch")
    @Operation(summary = "Process batch transfers", description = "Process up to 20 transfers concurrently")
    @ApiResponse(responseCode = "200", description = "Batch processed (check individual transfer statuses)")
    @ApiResponse(responseCode = "400", description = "Invalid batch request or too many transfers")
    public ResponseEntity<BatchTransferResponse> processBatchTransfers(
            @Valid @RequestBody BatchTransferRequestDto request) {

        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        try {
            log.info("Received batch transfer request with {} transfers [correlationId={}]",
                    request.getTransfers().size(), correlationId);

            // Convert DTOs to service objects
            List<CreateTransferRequestDto> serviceRequests = request.getTransfers().stream()
                    .map(dto -> new CreateTransferRequestDto(
                            dto.getFromAccountId(),
                            dto.getToAccountId(),
                            dto.getAmount()
                    ))
                    .collect(Collectors.toList());

            List<Transfer> results = transferService.processBatchTransfers(serviceRequests);

            List<TransferResponse> responses = results.stream()
                    .map(transfer -> {
                        TransferResponse response = TransferResponse.fromTransfer(transfer);
                        response.setCorrelationId(correlationId);
                        return response;
                    })
                    .collect(Collectors.toList());

            BatchTransferResponse batchResponse = new BatchTransferResponse(responses, correlationId);
            return ResponseEntity.ok(batchResponse);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid batch transfer request [correlationId={}]: {}", correlationId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error processing batch transfers [correlationId={}]", correlationId, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("correlationId");
        }
    }
}
