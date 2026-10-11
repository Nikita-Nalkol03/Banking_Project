package com.smartbank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.smartbank.entity.Account;
import com.smartbank.entity.AccountStatus;
import com.smartbank.entity.AccountType;
import com.smartbank.entity.BankTransaction;
import com.smartbank.entity.Role;
import com.smartbank.entity.TransactionStatus;
import com.smartbank.entity.TransactionType;
import com.smartbank.entity.User;
import com.smartbank.entity.UserStatus;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.BankTransactionRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.security.JwtService;

import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionSummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser;
    private User otherUser;

    private Account testAccount;
    private Account otherAccount;

    private String accessToken;

    private final String password = "TestPassword@123";

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        transactionTemplate.executeWithoutResult(transaction -> {
            testUser = createUser(
                    "summary-" + unique + "@example.com",
                    unique);


otherUser = createUser(
        "other-" + unique + "@example.com",
        UUID.randomUUID()
                .toString()
                .replace("-", ""));

            testUser = userRepository.save(testUser);
            otherUser = userRepository.save(otherUser);

            testAccount = createAccount(
                    "TS" + unique.substring(0, 18),
                    testUser);

            otherAccount = createAccount(
                    "TO" + unique.substring(0, 18),
                    otherUser);

            testAccount = accountRepository.save(testAccount);
            otherAccount = accountRepository.save(otherAccount);

            transactionRepository.save(createTransaction(
                    testAccount,
                    TransactionType.DEBIT,
                    "10.00",
                    "Summary debit one"));

            transactionRepository.save(createTransaction(
                    testAccount,
                    TransactionType.CREDIT,
                    "20.00",
                    "Summary credit"));

            transactionRepository.save(createTransaction(
                    testAccount,
                    TransactionType.DEBIT,
                    "30.00",
                    "Summary debit two"));
        });

        UserDetails userDetails =
                new org.springframework.security.core.userdetails.User(
                        testUser.getEmail(),
                        testUser.getPassword(),
                        java.util.List.of(
                                new SimpleGrantedAuthority("ROLE_USER")));

        accessToken = jwtService.generateAccessToken(userDetails);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(transaction -> {
            if (testAccount != null && otherAccount != null) {
                Long testAccountId = testAccount.getId();
                Long otherAccountId = otherAccount.getId();

                transactionRepository.findAll().stream()
                        .filter(bankTransaction ->
                                bankTransaction.getAccount() != null
                                && (
                                    bankTransaction.getAccount().getId()
                                            .equals(testAccountId)
                                    || bankTransaction.getAccount().getId()
                                            .equals(otherAccountId)
                                ))
                        .forEach(transactionRepository::delete);

                accountRepository.delete(testAccount);
                accountRepository.delete(otherAccount);
            }

            if (testUser != null) {
                userRepository.delete(testUser);
            }

            if (otherUser != null) {
                userRepository.delete(otherUser);
            }
        });
    }

    @Test
    void shouldReturnCorrectTransactionSummary() throws Exception {
        mockMvc.perform(get(summaryUrl(testAccount.getAccountNumber()))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(3))
                .andExpect(jsonPath("$.totalDebit").value(40.00))
                .andExpect(jsonPath("$.totalCredit").value(20.00))
                .andExpect(jsonPath("$.netMovement").value(-20.00));
    }

    @Test
    void shouldReturnSummaryForDateRange() throws Exception {
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(1);

        mockMvc.perform(get(summaryUrl(testAccount.getAccountNumber()))
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(3))
                .andExpect(jsonPath("$.totalDebit").value(40.00))
                .andExpect(jsonPath("$.totalCredit").value(20.00))
                .andExpect(jsonPath("$.netMovement").value(-20.00));
    }

    @Test
    void shouldRejectSummaryForAnotherUsersAccount() throws Exception {
        mockMvc.perform(get(summaryUrl(otherAccount.getAccountNumber()))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnZeroSummaryWhenNoTransactionsMatchDateRange()
            throws Exception {

        mockMvc.perform(get(summaryUrl(testAccount.getAccountNumber()))
                        .param("startDate", "2000-01-01")
                        .param("endDate", "2000-01-02")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(0))
                .andExpect(jsonPath("$.totalDebit").value(0))
                .andExpect(jsonPath("$.totalCredit").value(0))
                .andExpect(jsonPath("$.netMovement").value(0));
    }

    @Test
    void shouldRejectInvalidDateRange() throws Exception {
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now();

        mockMvc.perform(get(summaryUrl(testAccount.getAccountNumber()))
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest());
    }

    private User createUser(String email, String unique) {
        return User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .phone("9" + unique.substring(0, 9))
                .password(passwordEncoder.encode(password))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Account createAccount(String accountNumber, User user) {
        return Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();
    }

    private BankTransaction createTransaction(
            Account account,
            TransactionType type,
            String amount,
            String description) {

        return BankTransaction.builder()
                .transactionReference(
                        "TXN-" + UUID.randomUUID().toString()
                                .replace("-", ""))
                .account(account)
                .type(type)
                .amount(new BigDecimal(amount))
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .build();
    }

    private String summaryUrl(String accountNumber) {
        return "/api/accounts/" + accountNumber + "/transactions/summary";
    }

    private String bearerToken() {
        return "Bearer " + accessToken;
    }
    

@Test
void shouldIncludeOnlySuccessfulTransactionsInSummary() throws Exception {
    transactionTemplate.executeWithoutResult(transaction -> {
        BankTransaction failedTransaction = BankTransaction.builder()
                .transactionReference(
                        "TXN-" + UUID.randomUUID().toString().replace("-", ""))
                .account(testAccount)
                .type(TransactionType.DEBIT)
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.FAILED)
                .description("Failed transaction")
                .build();

        transactionRepository.save(failedTransaction);
    });

    mockMvc.perform(get(summaryUrl(testAccount.getAccountNumber()))
                    .header(HttpHeaders.AUTHORIZATION, bearerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTransactions").value(3))
            .andExpect(jsonPath("$.totalDebit").value(40.00))
            .andExpect(jsonPath("$.totalCredit").value(20.00))
            .andExpect(jsonPath("$.netMovement").value(-20.00));
}
}