
package com.smartbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.smartbank.security.JwtService;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private static final String TRANSFER_URL = "/api/transfers";

    private static final String VALID_REQUEST = """
            {
                "senderAccountNumber": "12345678901",
                "receiverAccountNumber": "10987654321",
                "amount": 100.00,
                "description": "Security integration test"
            }
            """;

    @Test
    void missingJwtShouldBeUnauthorized() throws Exception {
        mockMvc.perform(post(TRANSFER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "security-test-missing")
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtShouldBeUnauthorized() throws Exception {
        mockMvc.perform(post(TRANSFER_URL)
                        .header("Authorization", "Bearer invalid.token.value")
                        .header("Idempotency-Key", "security-test-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtShouldPassAuthenticationAndReachEndpoint()
            throws Exception {

        UserDetails userDetails = User.withUsername(
                        "security-test@smartbank.test")
                .password("not-used")
                .authorities("ROLE_USER")
                .build();

        String token = jwtService.generateAccessToken(userDetails);

int responseStatus = mockMvc.perform(
        post(TRANSFER_URL)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "security-test-valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
        .andReturn()
        .getResponse()
        .getStatus();

System.out.println("VALID JWT TEST HTTP STATUS: " + responseStatus);

       
    }
}