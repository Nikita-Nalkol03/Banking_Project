
package com.smartbank;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRefreshLogoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String REFRESH_URL = "/api/auth/refresh";
    private static final String LOGOUT_URL = "/api/auth/logout";

    private record TestTokens(String accessToken, String refreshToken) {}

    private TestTokens registerAndLogin() throws Exception {

        String uniqueId = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String email = "refresh" + uniqueId + "@smartbank.test";
        String phone = "9" + uniqueId.substring(0, 9).replaceAll("[^0-9]", "0");

        
        
        String registerRequest = objectMapper.writeValueAsString(
                Map.of(
                        "firstName", "Test",
                        "lastName", "User",
                        "email", email,
                        "phone", phone,
                        "password", "TestPass123!"
                )
        );

        MvcResult registerResult = mockMvc.perform(
                post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andReturn();

        System.out.println("REGISTER STATUS: "
                + registerResult.getResponse().getStatus());
        System.out.println("REGISTER BODY: "
                + registerResult.getResponse().getContentAsString());

        if (registerResult.getResponse().getStatus() != 201) {
            throw new AssertionError(
                    "Registration failed. HTTP Status: "
                    + registerResult.getResponse().getStatus()
                    + "\nResponse: "
                    + registerResult.getResponse().getContentAsString()
            );
        }

        String loginRequest = objectMapper.writeValueAsString(
                Map.of(
                        "email", email,
                        "password", "TestPass123!"
                )
        );

        MvcResult loginResult = mockMvc.perform(
                post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andReturn();

        System.out.println("LOGIN STATUS: "
                + loginResult.getResponse().getStatus());
        System.out.println("LOGIN BODY: "
                + loginResult.getResponse().getContentAsString());

        if (loginResult.getResponse().getStatus() != 200) {
            throw new AssertionError("Login failed. See LOGIN BODY and application exception.");
        }

        JsonNode response = objectMapper.readTree(
                loginResult.getResponse().getContentAsString());

        return new TestTokens(
                response.get("accessToken").asText(),
                response.get("refreshToken").asText()
        );
    }

    private String refresh(String refreshToken) throws Exception {

        String request = objectMapper.writeValueAsString(
                Map.of("refreshToken", refreshToken));

        MvcResult result = mockMvc.perform(
                post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andReturn();

        System.out.println("REFRESH STATUS: "
                + result.getResponse().getStatus());
        System.out.println("REFRESH BODY: "
                + result.getResponse().getContentAsString());


org.junit.jupiter.api.Assertions.assertEquals(
        200,
        result.getResponse().getStatus(),
        "Refresh failed. HTTP Status: "
                + result.getResponse().getStatus()
                + "\nResponse: "
                + result.getResponse().getContentAsString()
);

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());

        assertNotNull(response.get("accessToken"));
        assertNotNull(response.get("refreshToken"));

        return response.get("refreshToken").asText();
    }

    @Test
    void validRefreshTokenShouldReturnNewTokens() throws Exception {
        TestTokens tokens = registerAndLogin();

        String newRefreshToken = refresh(tokens.refreshToken());

        assertNotNull(newRefreshToken);
        assertNotEquals(tokens.refreshToken(), newRefreshToken);
    }

    @Test
    void oldRefreshTokenShouldBeRejectedAfterRotation() throws Exception {
        TestTokens tokens = registerAndLogin();

        refresh(tokens.refreshToken());

        String request = objectMapper.writeValueAsString(
                Map.of("refreshToken", tokens.refreshToken()));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggedOutRefreshTokenShouldBeRejected() throws Exception {
        TestTokens tokens = registerAndLogin();

        String request = objectMapper.writeValueAsString(
                Map.of("refreshToken", tokens.refreshToken()));

        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRefreshTokenShouldBeRejected() throws Exception {
        String request = objectMapper.writeValueAsString(
                Map.of("refreshToken", "invalid.refresh.token"));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutShouldBeIdempotentForSameToken() throws Exception {
        TestTokens tokens = registerAndLogin();

        String request = objectMapper.writeValueAsString(
                Map.of("refreshToken", tokens.refreshToken()));

        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNoContent());
    }
}