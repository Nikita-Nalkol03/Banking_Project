package com.smartbank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smartbank.entity.TransactionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DepositResponse {

    private String transactionReference;

    private String accountNumber;

    private BigDecimal amount;

    private BigDecimal newBalance;

    private TransactionStatus status;

    private LocalDateTime createdAt;
}