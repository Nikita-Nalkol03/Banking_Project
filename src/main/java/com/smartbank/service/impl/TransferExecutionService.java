package com.smartbank.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartbank.dto.TransferRequest;
import com.smartbank.dto.TransferResponse;
import com.smartbank.entity.Account;
import com.smartbank.entity.AccountStatus;
import com.smartbank.entity.BankTransaction;
import com.smartbank.entity.IdempotencyRecord;
import com.smartbank.entity.TransactionStatus;
import com.smartbank.entity.TransactionType;
import com.smartbank.entity.User;
import com.smartbank.exception.InsufficientBalanceException;
import com.smartbank.exception.InvalidTransferException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.BankTransactionRepository;
import com.smartbank.repository.IdempotencyRecordRepository;
import com.smartbank.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferExecutionService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final UserRepository userRepository;

    @Transactional
    public TransferResponse execute(
            TransferRequest request,
            String idempotencyKey,
            Long userId,
            String requestHash) {

        validateRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTransferException(
                        "Authenticated user not found"
                ));

        String senderNumber = request.getSenderAccountNumber().trim();
        String receiverNumber = request.getReceiverAccountNumber().trim();

        if (senderNumber.equals(receiverNumber)) {
            throw new InvalidTransferException(
                    "Sender and receiver accounts must be different"
            );
        }

        /*
         * Reserve the idempotency key inside this transaction.
         * A concurrent duplicate key must fail the unique constraint.
         * If the transfer fails, this reservation rolls back too.
         */
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUser(user);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setStatus(TransactionStatus.PENDING);

        idempotencyRecordRepository.saveAndFlush(record);

        Account sender;
        Account receiver;

        // Lock both accounts in deterministic order to reduce deadlocks.
        if (senderNumber.compareTo(receiverNumber) < 0) {
            sender = lockAccount(senderNumber, "Sender");
            receiver = lockAccount(receiverNumber, "Receiver");
        } else {
            receiver = lockAccount(receiverNumber, "Receiver");
            sender = lockAccount(senderNumber, "Sender");
        }

        validateSenderOwnership(sender, user);
        validateAccountStatus(sender, "Sender");
        validateAccountStatus(receiver, "Receiver");

        BigDecimal amount = request.getAmount();

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        String transactionReference = generateTransactionReference();
        String description = request.getDescription();

        BankTransaction debit = BankTransaction.builder()
                .transactionReference(transactionReference + "-D")
                .account(sender)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .build();

        BankTransaction credit = BankTransaction.builder()
                .transactionReference(transactionReference + "-C")
                .account(receiver)
                .type(TransactionType.CREDIT)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .build();

        transactionRepository.save(debit);
        transactionRepository.save(credit);
        transactionRepository.flush();

        LocalDateTime responseCreatedAt = debit.getCreatedAt();

        if (responseCreatedAt == null) {
            responseCreatedAt = LocalDateTime.now();
        }

        // Store the successful response so a retry can replay it.
        record.setTransactionReference(transactionReference);
        record.setSenderAccountNumber(sender.getAccountNumber());
        record.setReceiverAccountNumber(receiver.getAccountNumber());
        record.setAmount(amount);
        record.setStatus(TransactionStatus.SUCCESS);
        record.setDescription(description);
        record.setResponseCreatedAt(responseCreatedAt);

        idempotencyRecordRepository.saveAndFlush(record);

        return new TransferResponse(
                transactionReference,
                sender.getAccountNumber(),
                receiver.getAccountNumber(),
                amount,
                TransactionStatus.SUCCESS,
                description,
                responseCreatedAt
        );
    }

    private Account lockAccount(String accountNumber, String role) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new InvalidTransferException(
                        role + " account not found"
                ));
    }

    private void validateRequest(TransferRequest request) {
        if (request == null) {
            throw new InvalidTransferException(
                    "Transfer request is required"
            );
        }

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException(
                    "Transfer amount must be greater than zero"
            );
        }

        if (request.getAmount().scale() > 2) {
            throw new InvalidTransferException(
                    "Transfer amount cannot have more than 2 decimal places"
            );
        }

        if (request.getSenderAccountNumber() == null
                || request.getSenderAccountNumber().isBlank()) {
            throw new InvalidTransferException(
                    "Sender account number is required"
            );
        }

        if (request.getReceiverAccountNumber() == null
                || request.getReceiverAccountNumber().isBlank()) {
            throw new InvalidTransferException(
                    "Receiver account number is required"
            );
        }
    }

    private void validateSenderOwnership(Account sender, User user) {
        if (!sender.getUser().getId().equals(user.getId())) {
            throw new InvalidTransferException(
                    "You are not authorized to use this sender account"
            );
        }
    }

    private void validateAccountStatus(Account account, String role) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException(
                    role + " account is not active"
            );
        }
    }

    private String generateTransactionReference() {
        return "TXN-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase();
    }
}