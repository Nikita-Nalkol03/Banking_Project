
package com.smartbank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionHistoryIntegrationTest {

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
    private EntityManager entityManager;
    
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
        transactionTemplate.executeWithoutResult(transaction -> {
            String unique = UUID.randomUUID().toString().replace("-", "");

            testUser = userRepository.save(
                    User.builder()
                            .firstName("History")
                            .lastName("Tester")
                            .email("history_" + unique + "@example.com")
                            .phone("9" + unique.substring(0, 9))
                            .password(passwordEncoder.encode(password))
                            .role(Role.USER)
                            .status(UserStatus.ACTIVE)
                            .build()
            );

            otherUser = userRepository.save(
                    User.builder()
                            .firstName("Other")
                            .lastName("Tester")
                            .email("other_" + unique + "@example.com")
                            .phone("8" + unique.substring(0, 9))
                            .password(passwordEncoder.encode(password))
                            .role(Role.USER)
                            .status(UserStatus.ACTIVE)
                            .build()
            );

            testAccount = accountRepository.save(
                    Account.builder()
                            .accountNumber("TH" + unique.substring(0, 18))
                            .accountType(AccountType.SAVINGS)
                            .balance(new BigDecimal("1000.00"))
                            .status(AccountStatus.ACTIVE)
                            .user(testUser)
                            .build()
            );

            otherAccount = accountRepository.save(
                    Account.builder()
                            .accountNumber("OH" + unique.substring(0, 18))
                            .accountType(AccountType.SAVINGS)
                            .balance(new BigDecimal("1000.00"))
                            .status(AccountStatus.ACTIVE)
                            .user(otherUser)
                            .build()
            );

            transactionRepository.saveAll(List.of(
                    createTransaction(testAccount, TransactionType.DEBIT,
                            "10.00", "History debit one"),
                    createTransaction(testAccount, TransactionType.CREDIT,
                            "20.00", "History credit"),
                    createTransaction(testAccount, TransactionType.DEBIT,
                            "30.00", "History debit two")
            ));
        });

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(testUser.getEmail())
                .password(password)
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();

        accessToken = jwtService.generateAccessToken(userDetails);
    }

    @AfterEach
    void cleanUp() {
        if (testUser == null || otherUser == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(transaction -> {
            List<Long> accountIds = List.of(
                    testAccount.getId(),
                    otherAccount.getId()
            );

            transactionRepository.findAll().stream()
                    .filter(bankTransaction ->
                            accountIds.contains(bankTransaction.getAccount().getId()))
                    .forEach(transactionRepository::delete);

            accountRepository.deleteAllById(accountIds);

            userRepository.delete(testUser);
            userRepository.delete(otherUser);
        });
    }

    @Test
    void shouldReturnTransactionsForOwnedAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldRejectAccessToAnotherUsersAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions",
                        otherAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldSupportPagination() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void shouldSortTransactionsByAmountDescending() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("sortBy", "amount")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(30.00))
                .andExpect(jsonPath("$.content[1].amount").value(20.00))
                .andExpect(jsonPath("$.content[2].amount").value(10.00));
    }

    @Test
    void shouldFilterTransactionsByType() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.content[1].type").value("DEBIT"));
    }

    @Test
    void shouldRejectInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }
    
   
    @Test
    void shouldFilterTransactionsByDateRange() throws Exception {
        mockMvc.perform(get(
                        "/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("startDate", LocalDate.now().minusDays(1).toString())
                        .param("endDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldIncludeEndDateInDateRange() throws Exception {
        LocalDate today = LocalDate.now();

        mockMvc.perform(get(
                        "/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("startDate", today.toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldRejectStartDateAfterEndDate() throws Exception {
        mockMvc.perform(get(
                        "/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("startDate", "2026-10-10")
                        .param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCombineDateRangeWithTransactionType() throws Exception {
        mockMvc.perform(get(
                        "/api/accounts/{accountNumber}/transactions",
                        testAccount.getAccountNumber())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .param("startDate", LocalDate.now().minusDays(1).toString())
                        .param("endDate", LocalDate.now().plusDays(1).toString())
                        .param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.content[1].type").value("DEBIT"));
    }
   


    private String bearerToken() {
        return "Bearer " + accessToken;
    }

    private BankTransaction createTransaction(
            Account account,
            TransactionType type,
            String amount,
            String description) {

        return BankTransaction.builder()
                .transactionReference("TH-" + UUID.randomUUID())
                .account(account)
                .type(type)
                .amount(new BigDecimal(amount))
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .build();
    }
}