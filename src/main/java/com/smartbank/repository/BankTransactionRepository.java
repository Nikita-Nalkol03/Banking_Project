
package com.smartbank.repository;

import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.smartbank.entity.BankTransaction;
import com.smartbank.entity.TransactionType;

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
}