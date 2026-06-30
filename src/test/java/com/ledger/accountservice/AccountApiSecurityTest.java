package com.ledger.accountservice;

import com.ledger.accountservice.web.InternalApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the {@code /accounts/**} business APIs are restricted to callers presenting the shared
 * internal API key (i.e. the Event Service), while {@code /health} stays open. Uses a plain MockMvc so it
 * controls the header per request.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountApiSecurityTest {

    private static final String VALID_KEY = "local-internal-api-key";

    @Autowired
    MockMvc mockMvc;

    private String txn() {
        return """
                {"transactionId":"sec-1","type":"CREDIT","amount":10.00,"currency":"USD","eventTimestamp":"2026-05-15T10:00:00Z"}
                """;
    }

    @Test
    void transaction_withoutApiKey_isUnauthorized() throws Exception {
        mockMvc.perform(post("/accounts/{id}/transactions", "acc-sec")
                        .contentType(MediaType.APPLICATION_JSON).content(txn()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void transaction_withWrongApiKey_isUnauthorized() throws Exception {
        mockMvc.perform(post("/accounts/{id}/transactions", "acc-sec")
                        .header(InternalApiKeyFilter.API_KEY_HEADER, "not-the-key")
                        .contentType(MediaType.APPLICATION_JSON).content(txn()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void balance_withValidApiKey_passesTheFilter() throws Exception {
        // 404 (unknown account) proves the request got past the API-key filter into the controller.
        mockMvc.perform(get("/accounts/{id}/balance", "no-such-account")
                        .header(InternalApiKeyFilter.API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void health_isOpenWithoutApiKey() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
