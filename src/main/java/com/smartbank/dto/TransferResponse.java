package com.smartbank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smartbank.entity.TransactionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferResponse {

    private String transactionReference;

    private String senderAccountNumber;

    private String receiverAccountNumber;

    private BigDecimal amount;

    private TransactionStatus status;

    private String description;

    private LocalDateTime createdAt;
}