package com.smartbank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smartbank.entity.TransactionStatus;
import com.smartbank.entity.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransactionHistoryResponse {

    private String transactionReference;
    private String accountNumber;
    private TransactionType type;
    private BigDecimal amount;
    private TransactionStatus status;
    private String description;
    private LocalDateTime createdAt;
}