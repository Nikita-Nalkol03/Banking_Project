package com.smartbank.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartbank.dto.DepositRequest;
import com.smartbank.dto.DepositResponse;
import com.smartbank.entity.Account;
import com.smartbank.entity.AccountStatus;
import com.smartbank.entity.BankTransaction;
import com.smartbank.entity.TransactionStatus;
import com.smartbank.entity.TransactionType;
import com.smartbank.exception.InvalidTransferException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.BankTransactionRepository;
import com.smartbank.service.DepositService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepositServiceImpl implements DepositService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;

    @Override
    @Transactional
    public DepositResponse deposit(
            String accountNumber,
            DepositRequest request) {

        validateAmount(request.getAmount());

        Account account = accountRepository
                .findByAccountNumberForUpdate(accountNumber.trim())
                .orElseThrow(() ->
                        new InvalidTransferException(
                                "Account not found"
                        )
                );

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException(
                    "Account is not active"
            );
        }

        BigDecimal amount = request.getAmount();

        account.setBalance(
                account.getBalance().add(amount)
        );

        String transactionReference =
                generateTransactionReference();

        BankTransaction transaction =
                BankTransaction.builder()
                        .transactionReference(transactionReference)
                        .account(account)
                        .type(TransactionType.CREDIT)
                        .amount(amount)
                        .status(TransactionStatus.SUCCESS)
                        .description("Admin account funding")
                        .build();

        transactionRepository.save(transaction);

        return new DepositResponse(
                transactionReference,
                account.getAccountNumber(),
                amount,
                account.getBalance(),
                TransactionStatus.SUCCESS,
                transaction.getCreatedAt()
        );
    }

    private void validateAmount(BigDecimal amount) {

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new InvalidTransferException(
                    "Deposit amount must be greater than zero"
            );
        }

        if (amount.scale() > 2) {

            throw new InvalidTransferException(
                    "Deposit amount cannot have more than 2 decimal places"
            );
        }
    }

    private String generateTransactionReference() {

        return "DEP-" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 20)
                        .toUpperCase();
    }
}