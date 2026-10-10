
package com.smartbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.smartbank.entity.TransactionType;
import com.smartbank.entity.User;
import com.smartbank.entity.UserStatus;
import com.smartbank.exception.InsufficientBalanceException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.BankTransactionRepository;
import com.smartbank.repository.IdempotencyRecordRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.TransferService;

@SpringBootTest
class TransferConcurrencyIntegrationTest {

    private static final int TRANSFER_COUNT = 20;
    private static final int THREAD_COUNT = 10;

    private static final BigDecimal INITIAL_SENDER_BALANCE =
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
        testEmail = "concurrency-" + UUID.randomUUID() + "@smartbank.test";
        senderAccountNumber = generateAccountNumber();
        receiverAccountNumber = generateAccountNumber();

        transactionTemplate.executeWithoutResult(status -> {
            User user = User.builder()
                    .firstName("Concurrency")
                    .lastName("Tester")
                    .email(testEmail)
                    .phone(generatePhoneNumber())
                    .password(passwordEncoder.encode(
                            UUID.randomUUID().toString()
                    ))
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)
                    .build();

            userRepository.saveAndFlush(user);
            testUserId = user.getId();

            Account sender = Account.builder()
                    .accountNumber(senderAccountNumber)
                    .accountType(AccountType.SAVINGS)
                    .balance(INITIAL_SENDER_BALANCE)
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
    void concurrentTransfersShouldPreserveBalancesAndLedger()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(THREAD_COUNT);

        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<TransferResponse>> futures =
                    java.util.stream.IntStream
                            .range(0, TRANSFER_COUNT)
                            .mapToObj(index -> executor.submit(() -> {
                                var authentication =
                                        UsernamePasswordAuthenticationToken.authenticated(
                                                testEmail,
                                                null,
                                                List.of(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_USER"
                                                        )
                                                )
                                        );

                                SecurityContextHolder.getContext()
                                        .setAuthentication(authentication);

                                try {
                                    ready.countDown();

                                    if (!start.await(15, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting to start"
                                        );
                                    }

                                    TransferRequest request =
                                            new TransferRequest();

                                    request.setSenderAccountNumber(
                                            senderAccountNumber
                                    );
                                    request.setReceiverAccountNumber(
                                            receiverAccountNumber
                                    );
                                    request.setAmount(TRANSFER_AMOUNT);
                                    request.setDescription(
                                            "Concurrent integration test"
                                    );

                                    // Each independent transfer needs its own key.
                                    String idempotencyKey =
                                            UUID.randomUUID().toString();

                                    return transferService.transfer(
                                            request,
                                            idempotencyKey
                                    );
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            }))
                            .toList();

            assertTrue(
                    ready.await(15, TimeUnit.SECONDS),
                    "Workers did not become ready in time"
            );

            start.countDown();

            for (Future<TransferResponse> future : futures) {
                TransferResponse response =
                        future.get(60, TimeUnit.SECONDS);

                assertEquals(
                        TransactionStatus.SUCCESS,
                        response.getStatus()
                );
            }
        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(15, TimeUnit.SECONDS),
                    "Executor did not terminate"
            );
        }

        transactionTemplate.executeWithoutResult(status -> {
            Account sender = accountRepository
                    .findById(senderAccountId)
                    .orElseThrow();

            Account receiver = accountRepository
                    .findById(receiverAccountId)
                    .orElseThrow();

            assertEquals(
                    new BigDecimal("3000.00"),
                    sender.getBalance()
            );

            assertEquals(
                    new BigDecimal("2000.00"),
                    receiver.getBalance()
            );

            List<BankTransaction> transactions =
                    transactionRepository.findAll()
                            .stream()
                            .filter(transaction ->
                                    transaction.getAccount().getId()
                                            .equals(senderAccountId)
                                    || transaction.getAccount().getId()
                                            .equals(receiverAccountId)
                            )
                            .toList();

            assertEquals(
                    TRANSFER_COUNT * 2,
                    transactions.size(),
                    "Every transfer must create debit and credit records"
            );

            assertEquals(
                    TRANSFER_COUNT,
                    transactions.stream()
                            .filter(transaction ->
                                    transaction.getAccount().getId()
                                            .equals(senderAccountId)
                                    && transaction.getType()
                                            == TransactionType.DEBIT
                                    && transaction.getStatus()
                                            == TransactionStatus.SUCCESS
                            )
                            .count()
            );

            assertEquals(
                    TRANSFER_COUNT,
                    transactions.stream()
                            .filter(transaction ->
                                    transaction.getAccount().getId()
                                            .equals(receiverAccountId)
                                    && transaction.getType()
                                            == TransactionType.CREDIT
                                    && transaction.getStatus()
                                            == TransactionStatus.SUCCESS
                            )
                            .count()
            );
        });
    }

    @Test
    void insufficientBalanceShouldNotChangeBalancesOrCreateTransactions() {
        TransferRequest request = new TransferRequest();
        request.setSenderAccountNumber(senderAccountNumber);
        request.setReceiverAccountNumber(receiverAccountNumber);
        request.setAmount(new BigDecimal("6000.00"));
        request.setDescription("Insufficient balance integration test");

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        testEmail,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            assertThrows(
                    InsufficientBalanceException.class,
                    () -> transferService.transfer(
                            request,
                            UUID.randomUUID().toString()
                    )
            );
        } finally {
            SecurityContextHolder.clearContext();
        }

        transactionTemplate.executeWithoutResult(status -> {
            Account sender = accountRepository
                    .findById(senderAccountId)
                    .orElseThrow();

            Account receiver = accountRepository
                    .findById(receiverAccountId)
                    .orElseThrow();

            assertEquals(
                    new BigDecimal("5000.00"),
                    sender.getBalance()
            );

            assertEquals(
                    new BigDecimal("0.00"),
                    receiver.getBalance()
            );

            long testTransactions = transactionRepository.findAll()
                    .stream()
                    .filter(transaction ->
                            transaction.getAccount().getId()
                                    .equals(senderAccountId)
                            || transaction.getAccount().getId()
                                    .equals(receiverAccountId)
                    )
                    .count();

            assertEquals(0, testTransactions);
        });
    }

    @AfterEach
    void cleanUp() {
        if (testUserId == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            // Remove idempotency rows before deleting their owning user.
            idempotencyRecordRepository.deleteByUserId(testUserId);

            if (senderAccountId != null && receiverAccountId != null) {
                List<BankTransaction> testTransactions =
                        transactionRepository.findAll()
                                .stream()
                                .filter(transaction ->
                                        transaction.getAccount().getId()
                                                .equals(senderAccountId)
                                        || transaction.getAccount().getId()
                                                .equals(receiverAccountId)
                                )
                                .toList();

                transactionRepository.deleteAll(testTransactions);

                accountRepository.deleteAllById(
                        List.of(senderAccountId, receiverAccountId)
                );
            }

            userRepository.deleteById(testUserId);
        });
    }

    private String generateAccountNumber() {
        return Long.toString(
                10_000_000_000L
                + Math.floorMod(
                        UUID.randomUUID().getMostSignificantBits(),
                        89_999_999_999L
                )
        );
    }

    private String generatePhoneNumber() {
        return Long.toString(
                100_000_000_000_000L
                + Math.floorMod(
                        UUID.randomUUID().getLeastSignificantBits(),
                        899_999_999_999_999L
                )
        );
    }
}

