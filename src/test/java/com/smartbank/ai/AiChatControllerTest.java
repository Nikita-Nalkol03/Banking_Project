
package com.smartbank.ai;

import com.smartbank.ai.dto.AiChatRequest;
import com.smartbank.dto.TransactionSummaryResponse;
import com.smartbank.service.TransactionHistoryService;
import com.smartbank.security.JwtService;
import org.springframework.security.web.SecurityFilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;

@WebMvcTest(AiChatController.class)
class AiChatControllerTest {

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private SecurityFilterChain securityFilterChain;
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private TransactionHistoryService transactionHistoryService;

    @Test
    void chatShouldReturn200ForValidRequest() throws Exception {

        when(transactionHistoryService.getAccountTransactionSummary(
                eq("ACC123"), any(), any()))
                .thenReturn(new TransactionSummaryResponse(
                        3L,
                        new BigDecimal("40.00"),
                        new BigDecimal("20.00"),
                        new BigDecimal("-20.00")
                ));

        when(geminiService.generateResponse(anyString()))
                .thenReturn("Your spending is higher than your credits.");

        mockMvc.perform(post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "Summarize my spending",
                      "accountNumber": "ACC123",
                      "startDate": "2026-10-01",
                      "endDate": "2026-10-11"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response")
                        .value("Your spending is higher than your credits."));
    }

    @Test
    void chatShouldReturn400WhenMessageIsBlank() throws Exception {

        mockMvc.perform(post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "",
                      "accountNumber": "ACC123"
                    }
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatShouldReturn400WhenDateRangeIsInvalid() throws Exception {

        mockMvc.perform(post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "Summarize spending",
                      "accountNumber": "ACC123",
                      "startDate": "2026-10-11",
                      "endDate": "2026-10-01"
                    }
                    """))
                .andExpect(status().isBadRequest());
    }

@Test
void chatShouldCompareSelectedPeriodWithPreviousEqualLengthPeriod()
        throws Exception {

    when(transactionHistoryService.getAccountTransactionSummary(
            eq("ACC123"), any(), any()))
            .thenReturn(
                    new TransactionSummaryResponse(
                            3L,
                            new BigDecimal("40.00"),
                            new BigDecimal("20.00"),
                            new BigDecimal("-20.00")),
                    new TransactionSummaryResponse(
                            2L,
                            new BigDecimal("25.00"),
                            new BigDecimal("10.00"),
                            new BigDecimal("-15.00"))
            );

    when(geminiService.generateResponse(anyString()))
            .thenReturn("Spending increased by 15.00.");

    mockMvc.perform(post("/api/ai/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "message": "Compare my spending",
                  "accountNumber": "ACC123",
                  "startDate": "2026-10-01",
                  "endDate": "2026-10-11"
                }
                """))
            .andExpect(status().isOk());

    ArgumentCaptor<String> promptCaptor =
            ArgumentCaptor.forClass(String.class);

    verify(geminiService).generateResponse(promptCaptor.capture());

    String prompt = promptCaptor.getValue();

    assertTrue(prompt.contains("2026-10-01 to 2026-10-11"));
    assertTrue(prompt.contains("2026-09-20"));
    assertTrue(prompt.contains("2026-09-30"));
    assertTrue(prompt.contains("25.00"));
}
}