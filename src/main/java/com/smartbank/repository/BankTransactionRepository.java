
package com.smartbank.repository;

import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.smartbank.entity.BankTransaction;
import com.smartbank.entity.TransactionType;
import java.math.BigDecimal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartbank.entity.TransactionStatus;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByTransactionReference(
            String transactionReference
    );

    boolean existsByTransactionReference(
            String transactionReference
    );

    Page<BankTransaction> findByAccountId(
            Long accountId,
            Pageable pageable
    );

    Page<BankTransaction> findByAccountIdAndType(
            Long accountId,
            TransactionType type,
            Pageable pageable
    );
    
    Page<BankTransaction>
    findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long accountId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );
    
    Page<BankTransaction> findByAccount_User_Email(
            String email,
            Pageable pageable
    );

    Page<BankTransaction> findByAccount_User_EmailAndType(
            String email,
            TransactionType type,
            Pageable pageable
    );
    
    Page<BankTransaction> findByAccountIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long accountId,
            TransactionType type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
    
 
    @Query("""
        SELECT
            COUNT(bt) AS totalTransactions,
            COALESCE(SUM(
                CASE WHEN bt.type = :debitType
                     THEN bt.amount ELSE 0 END
            ), 0) AS totalDebit,
            COALESCE(SUM(
                CASE WHEN bt.type = :creditType
                     THEN bt.amount ELSE 0 END
            ), 0) AS totalCredit
        FROM BankTransaction bt
        WHERE bt.account.id = :accountId
          AND bt.status = :status
          AND (:startDate IS NULL OR bt.createdAt >= :startDate)
          AND (:endDate IS NULL OR bt.createdAt < :endDate)
        """)
    TransactionSummaryProjection getTransactionSummary(
            @Param("accountId") Long accountId,
            @Param("debitType") TransactionType debitType,
            @Param("creditType") TransactionType creditType,
            @Param("status") TransactionStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}