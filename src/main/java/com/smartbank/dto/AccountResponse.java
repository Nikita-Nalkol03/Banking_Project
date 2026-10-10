package com.smartbank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smartbank.entity.AccountStatus;
import com.smartbank.entity.AccountType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountResponse {

    private Long id;

    private String accountNumber;

    private AccountType accountType;

    private BigDecimal balance;

    private AccountStatus status;

    private LocalDateTime createdAt;
}