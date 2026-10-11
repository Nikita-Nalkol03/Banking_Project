
package com.smartbank.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartbank.dto.TransactionHistoryResponse;
import com.smartbank.entity.Account;
import com.smartbank.entity.BankTransaction;
import com.smartbank.entity.TransactionType;
import com.smartbank.entity.User;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.BankTransactionRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.TransactionHistoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.smartbank.dto.TransactionSummaryResponse;
import com.smartbank.entity.TransactionStatus;
import com.smartbank.repository.TransactionSummaryProjection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionHistoryServiceImpl
        implements TransactionHistoryService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> getAccountTransactions(
            String accountNumber,
            TransactionType type,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));

        Account account = accountRepository
                .findByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to view this account's transactions");
        }

        Page<BankTransaction> transactions;

        if (startDate == null && endDate == null) {

            transactions = type == null
                    ? transactionRepository.findByAccountId(
                            account.getId(), pageable)
                    : transactionRepository.findByAccountIdAndType(
                            account.getId(), type, pageable);

        } else {

            LocalDateTime startDateTime = startDate.atStartOfDay();

            // Exclusive upper boundary includes the entire end date.
            LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

            transactions = type == null
                    ? transactionRepository
                            .findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                    account.getId(),
                                    startDateTime,
                                    endDateTime,
                                    pageable)
                    : transactionRepository
                            .findByAccountIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                    account.getId(),
                                    type,
                                    startDateTime,
                                    endDateTime,
                                    pageable);
        }

        return transactions.map(transaction ->
                new TransactionHistoryResponse(
                        transaction.getTransactionReference(),
                        account.getAccountNumber(),
                        transaction.getType(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getDescription(),
                        transaction.getCreatedAt()));
    }
   
    @Override
    @Transactional(readOnly = true)
    public TransactionSummaryResponse getAccountTransactionSummary(
            String accountNumber,
            LocalDate startDate,
            LocalDate endDate) {

        if ((startDate == null) != (endDate == null)) {
            throw new IllegalArgumentException(
                    "Both startDate and endDate must be provided together");
        }

        if (startDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "startDate must not be after endDate");
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));

        Account account = accountRepository
                .findByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to view this account's transactions");
        }

        LocalDateTime startDateTime = startDate == null
                ? null : startDate.atStartOfDay();

        LocalDateTime endDateTime = endDate == null
                ? null : endDate.plusDays(1).atStartOfDay();

        TransactionSummaryProjection summary =
                transactionRepository.getTransactionSummary(
                        account.getId(),
                        TransactionType.DEBIT,
                        TransactionType.CREDIT,
                        TransactionStatus.SUCCESS,
                        startDateTime,
                        endDateTime);

        BigDecimal debit = summary.getTotalDebit();
        BigDecimal credit = summary.getTotalCredit();

        return new TransactionSummaryResponse(
                summary.getTotalTransactions(),
                debit,
                credit,
                credit.subtract(debit));
    }
}
