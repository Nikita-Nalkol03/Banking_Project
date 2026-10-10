
package com.smartbank;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
import com.smartbank.entity.Account;
import com.smartbank.entity.AccountStatus;
import com.smartbank.entity.AccountType;
import com.smartbank.entity.BankTransaction;
import com.smartbank.entity.Role;
import com.smartbank.entity.User;
import com.smartbank.entity.UserStatus;
import com.smartbank.exception.InvalidTransferException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.BankTransactionRepository;
import com.smartbank.repository.IdempotencyRecordRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.TransferService;

@SpringBootTest
class TransferValidationIntegrationTest {

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
    private Long secondUserId;

    private Long senderAccountId;
    private Long receiverAccountId;
    private Long foreignAccountId;

    private String testEmail;
    private String senderAccountNumber;
    private String receiverAccountNumber;
    private String foreignAccountNumber;

    @BeforeEach
    void setUp() {
        testEmail = "validation-" + UUID.randomUUID()
                + "@smartbank.test";

        senderAccountNumber = generateAccountNumber();
        receiverAccountNumber = generateAccountNumber();
        foreignAccountNumber = generateAccountNumber();

        transactionTemplate.executeWithoutResult(status -> {
            User testUser = createUser(
                    "Validation", "Tester", testEmail);

            userRepository.saveAndFlush(testUser);
            testUserId = testUser.getId();

            User secondUser = createUser(
                    "Second", "User",
                    "second-" + UUID.randomUUID()
                            + "@smartbank.test");

            userRepository.saveAndFlush(secondUser);
            secondUserId = secondUser.getId();

            Account sender = createAccount(
                    senderAccountNumber,
                    new BigDecimal("5000.00"),
                    testUser);

            Account receiver = createAccount(
                    receiverAccountNumber,
                    new BigDecimal("0.00"),
                    testUser);

            Account foreignAccount = createAccount(
                    foreignAccountNumber,
                    new BigDecimal("1000.00"),
                    secondUser);

            accountRepository.saveAndFlush(sender);
            accountRepository.saveAndFlush(receiver);
            accountRepository.saveAndFlush(foreignAccount);

            senderAccountId = sender.getId();
            receiverAccountId = receiver.getId();
            foreignAccountId = foreignAccount.getId();
        });
    }

    @Test
    void anotherUsersAccountCannotBeUsedAsSender() {
        TransferRequest request = createRequest(
                foreignAccountNumber,
                receiverAccountNumber,
                new BigDecimal("100.00"));

        assertThrows(
                InvalidTransferException.class,
                () -> transferAsTestUser(
                        request, UUID.randomUUID().toString()));
    }

    @Test
    void inactiveSenderAccountShouldRejectTransfer() {
        transactionTemplate.executeWithoutResult(status -> {
            Account sender = accountRepository
                    .findById(senderAccountId)
                    .orElseThrow();

            sender.setStatus(AccountStatus.BLOCKED);
            accountRepository.saveAndFlush(sender);
        });

        TransferRequest request = createRequest(
                senderAccountNumber,
                receiverAccountNumber,
                new BigDecimal("100.00"));

        assertThrows(
                InvalidTransferException.class,
                () -> transferAsTestUser(
                        request, UUID.randomUUID().toString()));
    }

    @Test
    void inactiveReceiverAccountShouldRejectTransfer() {
        transactionTemplate.executeWithoutResult(status -> {
            Account receiver = accountRepository
                    .findById(receiverAccountId)
                    .orElseThrow();
            
            receiver.setStatus(AccountStatus.BLOCKED);
            accountRepository.saveAndFlush(receiver);
        });

        TransferRequest request = createRequest(
                senderAccountNumber,
                receiverAccountNumber,
                new BigDecimal("100.00"));

        assertThrows(
                InvalidTransferException.class,
                () -> transferAsTestUser(
                        request, UUID.randomUUID().toString()));
    }

    @Test
    void zeroAmountShouldBeRejected() {
        TransferRequest request = createRequest(
                senderAccountNumber,
                receiverAccountNumber,
                BigDecimal.ZERO);

        assertThrows(
                InvalidTransferException.class,
                () -> transferAsTestUser(
                        request, UUID.randomUUID().toString()));
    }

    @Test
    void negativeAmountShouldBeRejected() {
        TransferRequest request = createRequest(
                senderAccountNumber,
                receiverAccountNumber,
                new BigDecimal("-100.00"));

        assertThrows(
                InvalidTransferException.class,
                () -> transferAsTestUser(
                        request, UUID.randomUUID().toString()));
    }

    private User createUser(
            String firstName, String lastName, String email) {

        return User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(generatePhoneNumber())
                .password(passwordEncoder.encode(
                        UUID.randomUUID().toString()))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Account createAccount(
            String accountNumber,
            BigDecimal balance,
            User user) {

        return Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.SAVINGS)
                .balance(balance)
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();
    }

    private TransferRequest createRequest(
            String sender,
            String receiver,
            BigDecimal amount) {

        TransferRequest request = new TransferRequest();
        request.setSenderAccountNumber(sender);
        request.setReceiverAccountNumber(receiver);
        request.setAmount(amount);
        request.setDescription("Validation integration test");

        return request;
    }

    private void transferAsTestUser(
            TransferRequest request, String idempotencyKey) {

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        testEmail,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            transferService.transfer(request, idempotencyKey);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @AfterEach
    void cleanUp() {
        if (testUserId == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            idempotencyRecordRepository.deleteByUserId(testUserId);

            if (secondUserId != null) {
                idempotencyRecordRepository.deleteByUserId(secondUserId);
            }

            List<Long> accountIds = List.of(
                    senderAccountId,
                    receiverAccountId,
                    foreignAccountId);

            List<BankTransaction> transactions =
                    transactionRepository.findAll()
                            .stream()
                            .filter(transaction ->
                                    accountIds.contains(
                                            transaction.getAccount().getId()))
                            .toList();

            transactionRepository.deleteAll(transactions);
            accountRepository.deleteAllById(accountIds);

            if (secondUserId != null) {
                userRepository.deleteById(secondUserId);
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

