package com.smartbank.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartbank.dto.AccountResponse;
import com.smartbank.dto.CreateAccountRequest;
import com.smartbank.entity.Account;
import com.smartbank.entity.AccountStatus;
import com.smartbank.entity.User;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.AccountService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {

        User user = getAuthenticatedUser();

        String accountNumber = generateUniqueAccountNumber();

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();

        Account savedAccount = accountRepository.save(account);

        return mapToResponse(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts() {

        User user = getAuthenticatedUser();

        return accountRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private User getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new IllegalStateException(
                    "User is not authenticated"
            );
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"
                        )
                );
    }

    private String generateUniqueAccountNumber() {

        String accountNumber;

        do {
            accountNumber = generateAccountNumber();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    private String generateAccountNumber() {

        long number = ThreadLocalRandom.current()
                .nextLong(
                        10_000_000_000L,
                        100_000_000_000L
                );

        return String.valueOf(number);
    }

    private AccountResponse mapToResponse(Account account) {

        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}