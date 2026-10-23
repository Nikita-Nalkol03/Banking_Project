
package com.smartbank.ai;

import com.smartbank.ai.dto.AiChatRequest;
import com.smartbank.ai.dto.AiChatResponse;
import com.smartbank.dto.TransactionSummaryResponse;
import com.smartbank.service.TransactionHistoryService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final GeminiService geminiService;
    private final TransactionHistoryService transactionHistoryService;

    public AiChatController(
            GeminiService geminiService,
            TransactionHistoryService transactionHistoryService) {
        this.geminiService = geminiService;
        this.transactionHistoryService = transactionHistoryService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request) {

        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();

        if ((startDate == null) != (endDate == null)) {
            throw new IllegalArgumentException(
                    "Both startDate and endDate must be provided together");
        }

        if (startDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "startDate must not be after endDate");
        }

        TransactionSummaryResponse currentSummary =
                transactionHistoryService.getAccountTransactionSummary(
                        request.accountNumber(),
                        startDate,
                        endDate);

        TransactionSummaryResponse previousSummary = null;
        LocalDate previousStartDate = null;
        LocalDate previousEndDate = null;

        if (startDate != null) {

            long periodDays =
                    ChronoUnit.DAYS.between(startDate, endDate) + 1;

            previousEndDate = startDate.minusDays(1);
            previousStartDate = startDate.minusDays(periodDays);

            previousSummary =
                    transactionHistoryService.getAccountTransactionSummary(
                            request.accountNumber(),
                            previousStartDate,
                            previousEndDate);
        }

        String comparisonSection;

        if (previousSummary != null) {
            comparisonSection = """
                    
                    PREVIOUS EQUAL-LENGTH PERIOD:
                    Period: %s to %s
                    Total successful transactions: %s
                    Total debit: %s
                    Total credit: %s
                    Net movement (credit minus debit): %s

                    COMPARISON RULES:
                    Compare the current period with the previous period.
                    Calculate debit change as current debit minus previous debit.
                    Calculate credit change as current credit minus previous credit.
                    A positive debit change means spending increased.
                    A negative debit change means spending decreased.
                    If previous debit is zero, do not calculate a percentage
                    change; explain that a percentage cannot be calculated.
                    """.formatted(
                    previousStartDate,
                    previousEndDate,
                    previousSummary.getTotalTransactions(),
                    previousSummary.getTotalDebit(),
                    previousSummary.getTotalCredit(),
                    previousSummary.getNetMovement());
        } else {
            comparisonSection = """
                    
                    No date range was provided, so a previous-period
                    comparison is not available. Do not invent one.
                    """;
        }

        String prompt = """
                You are SmartBank AI, a helpful personal banking assistant.

                Use only the transaction summaries provided below.
                Do not invent transactions, balances, merchants,
                or spending categories.
                All summaries contain successful transactions only.
                Explain spending changes clearly using the supplied data.
                Offer practical saving suggestions where appropriate.
                Never ask for passwords, PINs, OTPs, or full card details.
                Do not claim a saving amount unless it is supported by data.
                
                CURRENT SELECTED PERIOD:
                Period: %s to %s
                Total successful transactions: %s
                Total debit: %s
                Total credit: %s
                Net movement (credit minus debit): %s
                %s
                
                User question: %s
                """.formatted(
                startDate == null ? "All available dates" : startDate,
                endDate == null ? "All available dates" : endDate,
                currentSummary.getTotalTransactions(),
                currentSummary.getTotalDebit(),
                currentSummary.getTotalCredit(),
                currentSummary.getNetMovement(),
                comparisonSection,
                request.message());

        String answer = geminiService.generateResponse(prompt);

        return ResponseEntity.ok(new AiChatResponse(answer));
    }
}