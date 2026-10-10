
package com.smartbank.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartbank.dto.TransactionHistoryResponse;
import com.smartbank.entity.TransactionType;

public interface TransactionHistoryService {

    Page<TransactionHistoryResponse> getAccountTransactions(
            String accountNumber,
            TransactionType type,
            Pageable pageable
    );
}