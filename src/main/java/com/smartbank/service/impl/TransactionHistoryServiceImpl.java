
package com.smartbank.service.impl;

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
            Pageable pageable) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        Account account = accountRepository
                .findByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to view this account's transactions"
            );
        }

        Page<BankTransaction> transactions =
                type == null
                        ? transactionRepository.findByAccountId(
                                account.getId(), pageable)
                        : transactionRepository.findByAccountIdAndType(
                                account.getId(), type, pageable);

        return transactions.map(transaction ->
                new TransactionHistoryResponse(
                        transaction.getTransactionReference(),
                        account.getAccountNumber(),
                        transaction.getType(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getDescription(),
                        transaction.getCreatedAt()
                )
        );
    }
}
