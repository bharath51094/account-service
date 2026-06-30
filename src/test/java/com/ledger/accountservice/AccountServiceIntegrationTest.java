package com.ledger.accountservice;

import com.ledger.accountservice.web.InternalApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Account Service against the real in-memory H2 database.
 * Covers the handout's core functionality that lives here: idempotency, balance computation,
 * order-independence, validation, the one-currency-per-account rule, and the read endpoints.
 * Each test uses a distinct accountId so they don't interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountServiceIntegrationTest {

    /**
     * These tests exercise business logic, not auth, so every request is given a valid internal API key
     * by default — set once here instead of on each call. Only applies to this test's context; the
     * dedicated {@code AccountApiSecurityTest} uses a plain MockMvc to assert the key is enforced.
     */
    @TestConfiguration
    static class ApiKeyHeaderConfig {
        @Bean
        MockMvcBuilderCustomizer internalApiKeyHeader() {
            return builder -> builder.defaultRequest(
                    get("/").header(InternalApiKeyFilter.API_KEY_HEADER, "local-internal-api-key"));
        }
    }

    @Autowired
    MockMvc mockMvc;

    private String txn(String transactionId, String type, String amount, String currency) {
        return """
                {"transactionId":"%s","type":"%s","amount":%s,"currency":"%s","eventTimestamp":"2026-05-15T10:00:00Z"}
                """.formatted(transactionId, type, amount, currency);
    }

    private void apply(String accountId, String body, int expectedStatus) throws Exception {
        mockMvc.perform(post("/accounts/{id}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void creditThenDebit_balanceIsNetSum() throws Exception {
        mockMvc.perform(post("/accounts/{id}/transactions", "acc-bal")
                        .contentType(MediaType.APPLICATION_JSON).content(txn("t1", "CREDIT", "100.00", "USD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(100.00));

        mockMvc.perform(post("/accounts/{id}/transactions", "acc-bal")
                        .contentType(MediaType.APPLICATION_JSON).content(txn("t2", "DEBIT", "30.00", "USD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(70.00));

        mockMvc.perform(get("/accounts/{id}/balance", "acc-bal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(70.00));
    }

    @Test
    void duplicateTransactionId_isIdempotent() throws Exception {
        apply("acc-dup", txn("dup", "CREDIT", "50.00", "USD"), 201);
        apply("acc-dup", txn("dup", "CREDIT", "50.00", "USD"), 200); // replay -> 200, not re-applied
        mockMvc.perform(get("/accounts/{id}/balance", "acc-dup"))
                .andExpect(jsonPath("$.balance").value(50.00)); // still 50, not 100
    }

    @Test
    void balanceIsOrderIndependent() throws Exception {
        apply("acc-ord", txn("o1", "CREDIT", "100.00", "USD"), 201);
        apply("acc-ord", txn("o2", "DEBIT", "40.00", "USD"), 201);
        mockMvc.perform(get("/accounts/{id}/balance", "acc-ord"))
                .andExpect(jsonPath("$.balance").value(60.00));
    }

    @Test
    void currencyMismatch_returns409() throws Exception {
        apply("acc-cur", txn("c1", "CREDIT", "10.00", "USD"), 201);
        apply("acc-cur", txn("c2", "CREDIT", "10.00", "EUR"), 409); // different currency on same account
    }

    @Test
    void invalidRequest_returns400WithFieldErrors() throws Exception {
        String bad = """
                {"transactionId":"","type":"FOO","amount":-5,"currency":"USD"}
                """;
        mockMvc.perform(post("/accounts/{id}/transactions", "acc-bad")
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void unknownAccountBalance_returns404() throws Exception {
        mockMvc.perform(get("/accounts/{id}/balance", "no-such-account"))
                .andExpect(status().isNotFound());
    }

    @Test
    void accountDetails_includeTransactions() throws Exception {
        apply("acc-det", txn("d1", "CREDIT", "20.00", "USD"), 201);
        mockMvc.perform(get("/accounts/{id}", "acc-det"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-det"))
                .andExpect(jsonPath("$.balance").value(20.00))
                .andExpect(jsonPath("$.transactions[0].transactionId").value("d1"));
    }
}
