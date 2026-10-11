
package com.smartbank.repository;

import java.math.BigDecimal;

public interface TransactionSummaryProjection {

    Long getTotalTransactions();

    BigDecimal getTotalDebit();

    BigDecimal getTotalCredit();
}

