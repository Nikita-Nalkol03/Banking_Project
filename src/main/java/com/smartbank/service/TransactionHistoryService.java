
package com.smartbank.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartbank.dto.TransactionHistoryResponse;
import com.smartbank.entity.TransactionType;
import java.time.LocalDate;
import com.smartbank.dto.TransactionSummaryResponse;

public interface TransactionHistoryService {

    Page<TransactionHistoryResponse> getAccountTransactions(
            String accountNumber,
            TransactionType type,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable);
    
    TransactionSummaryResponse getAccountTransactionSummary(
            String accountNumber,
            LocalDate startDate,
            LocalDate endDate);
}