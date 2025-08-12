package com.fintech.transfer_service.repository;

import com.fintech.transfer_service.data.TransferStatus;
import com.fintech.transfer_service.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transfer t WHERE t.fromAccountId = :accountId OR t.toAccountId = :accountId ORDER BY t.createdAt DESC")
    List<Transfer> findByAccountIdOrderByCreatedAtDesc(@Param("accountId") String accountId);

    @Query("SELECT t FROM Transfer t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<Transfer> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(t) FROM Transfer t WHERE t.status = :status")
    long countByStatus(@Param("status") TransferStatus status);
}
