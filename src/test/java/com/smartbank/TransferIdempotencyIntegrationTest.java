
package com.smartbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.smartbank.dto.TransferRequest;
import com.smartbank.dto.TransferResponse;
import com.smartbank.entity.Account;
import com.smartbank.entity.AccountStatus;
import com.smartbank.entity.AccountType;
import com.smartbank.entity.BankTransaction;
import com.smartbank.entity.Role;
import com.smartbank.entity.TransactionStatus;
import com.smartbank.entity.User;
import com.smartbank.entity.UserStatus;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.BankTransactionRepository;
import com.smartbank.repository.IdempotencyRecordRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.TransferService;

@SpringBootTest
class TransferIdempotencyIntegrationTest {

    private static final BigDecimal INITIAL_BALANCE =
            new BigDecimal("5000.00");

    private static final BigDecimal TRANSFER_AMOUNT =
            new BigDecimal("100.00");

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long testUserId;
    private Long senderAccountId;
    private Long receiverAccountId;

    private String testEmail;
    private String senderAccountNumber;
    private String receiverAccountNumber;

    @BeforeEach
    void setUp() {
        testEmail = "idempotency-" + UUID.randomUUID()
                + "@smartbank.test";

        senderAccountNumber = generateAccountNumber();
        receiverAccountNumber = generateAccountNumber();

        transactionTemplate.executeWithoutResult(status -> {
            User user = User.builder()
                    .firstName("Idempotency")
                    .lastName("Tester")
                    .email(testEmail)
                    .phone(generatePhoneNumber())
                    .password(passwordEncoder.encode(
                            UUID.randomUUID().toString()))
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)
                    .build();

            userRepository.saveAndFlush(user);
            testUserId = user.getId();

            Account sender = Account.builder()
                    .accountNumber(senderAccountNumber)
                    .accountType(AccountType.SAVINGS)
                    .balance(INITIAL_BALANCE)
                    .status(AccountStatus.ACTIVE)
                    .user(user)
                    .build();

            Account receiver = Account.builder()
                    .accountNumber(receiverAccountNumber)
                    .accountType(AccountType.SAVINGS)
                    .balance(new BigDecimal("0.00"))
                    .status(AccountStatus.ACTIVE)
                    .user(user)
                    .build();

            accountRepository.saveAndFlush(sender);
            accountRepository.saveAndFlush(receiver);

            senderAccountId = sender.getId();
            receiverAccountId = receiver.getId();
        });
    }

    @Test
    void sameKeyAndSameRequestShouldNotTransferTwice() {
        String key = UUID.randomUUID().toString();
        TransferRequest request = createRequest(
                TRANSFER_AMOUNT, "Idempotency replay test");

        TransferResponse firstResponse = transferAsTestUser(request, key);
        TransferResponse secondResponse = transferAsTestUser(request, key);

        assertEquals(
                firstResponse.getTransactionReference(),
                secondResponse.getTransactionReference(),
                "Retry must return the original transaction reference");

        assertEquals(
                firstResponse.getAmount(),
                secondResponse.getAmount());

        assertEquals(
                TransactionStatus.SUCCESS,
                secondResponse.getStatus());

        assertBalances("4900.00", "100.00");
        assertLedgerCount(2);
    }

    @Test
    void sameKeyAndDifferentRequestShouldReturnConflict() {
        String key = UUID.randomUUID().toString();

        TransferRequest firstRequest = createRequest(
                new BigDecimal("100.00"),
                "First request");

        TransferRequest differentRequest = createRequest(
                new BigDecimal("200.00"),
                "Different request");

        TransferResponse firstResponse =
                transferAsTestUser(firstRequest, key);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transferAsTestUser(differentRequest, key),
                "Reusing a key with a different request must fail");

        assertEquals(
                HttpStatus.CONFLICT.value(),
                exception.getStatusCode().value());

        assertNotEquals(
                null,
                firstResponse.getTransactionReference());

        assertBalances("4900.00", "100.00");
        assertLedgerCount(2);
    }

    @Test
    void differentKeysAndSameRequestShouldCreateTwoTransfers() {
        TransferRequest request = createRequest(
                TRANSFER_AMOUNT, "Independent transfer test");

        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();

        TransferResponse firstResponse =
                transferAsTestUser(request, firstKey);

        TransferResponse secondResponse =
                transferAsTestUser(request, secondKey);

        assertNotEquals(
                firstResponse.getTransactionReference(),
                secondResponse.getTransactionReference(),
                "Different keys should create separate transfers");

        assertEquals(
                TransactionStatus.SUCCESS,
                firstResponse.getStatus());

        assertEquals(
                TransactionStatus.SUCCESS,
                secondResponse.getStatus());

        assertBalances("4800.00", "200.00");
        assertLedgerCount(4);
    }

    private TransferRequest createRequest(
            BigDecimal amount, String description) {

        TransferRequest request = new TransferRequest();
        request.setSenderAccountNumber(senderAccountNumber);
        request.setReceiverAccountNumber(receiverAccountNumber);
        request.setAmount(amount);
        request.setDescription(description);

        return request;
    }

    private TransferResponse transferAsTestUser(
            TransferRequest request, String idempotencyKey) {

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        testEmail,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            return transferService.transfer(request, idempotencyKey);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertBalances(
            String expectedSenderBalance,
            String expectedReceiverBalance) {

        transactionTemplate.executeWithoutResult(status -> {
            Account sender = accountRepository
                    .findById(senderAccountId)
                    .orElseThrow();

            Account receiver = accountRepository
                    .findById(receiverAccountId)
                    .orElseThrow();

            assertEquals(
                    new BigDecimal(expectedSenderBalance),
                    sender.getBalance());

            assertEquals(
                    new BigDecimal(expectedReceiverBalance),
                    receiver.getBalance());
        });
    }

    private void assertLedgerCount(long expectedCount) {
        transactionTemplate.executeWithoutResult(status -> {
            long count = transactionRepository.findAll()
                    .stream()
                    .filter(transaction ->
                            transaction.getAccount().getId()
                                    .equals(senderAccountId)
                            || transaction.getAccount().getId()
                                    .equals(receiverAccountId))
                    .count();

            assertEquals(
                    expectedCount,
                    count,
                    "Ledger should contain only the expected debit/credit rows");
        });
    }

    @AfterEach
    void cleanUp() {
        if (testUserId == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            idempotencyRecordRepository.deleteByUserId(testUserId);

            if (senderAccountId != null && receiverAccountId != null) {
                List<BankTransaction> testTransactions =
                        transactionRepository.findAll()
                                .stream()
                                .filter(transaction ->
                                        transaction.getAccount().getId()
                                                .equals(senderAccountId)
                                        || transaction.getAccount().getId()
                                                .equals(receiverAccountId))
                                .toList();

                transactionRepository.deleteAll(testTransactions);

                accountRepository.deleteAllById(
                        List.of(senderAccountId, receiverAccountId));
            }

            userRepository.deleteById(testUserId);
        });
    }

    private String generateAccountNumber() {
        return Long.toString(
                10_000_000_000L
                + Math.floorMod(
                        UUID.randomUUID().getMostSignificantBits(),
                        89_999_999_999L));
    }

    private String generatePhoneNumber() {
        return Long.toString(
                100_000_000_000_000L
                + Math.floorMod(
                        UUID.randomUUID().getLeastSignificantBits(),
                        899_999_999_999_999L));
    }
}
