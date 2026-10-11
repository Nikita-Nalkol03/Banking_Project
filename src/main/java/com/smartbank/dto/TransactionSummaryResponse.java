
package com.smartbank.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransactionSummaryResponse {

    private Long totalTransactions;

    private BigDecimal totalDebit;

    private BigDecimal totalCredit;

    private BigDecimal netMovement;
}

