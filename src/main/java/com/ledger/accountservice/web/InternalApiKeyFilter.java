package com.ledger.accountservice.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.accountservice.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Restricts the {@code /accounts/**} business APIs to the Event Service. The Account Service is internal,
 * so every such request must present the shared secret in the {@code X-Internal-Api-Key} header; anything
 * missing or wrong is rejected with {@code 401}. Health checks and the H2 console are left open.
 *
 * <p>Runs just after {@link TraceIdFilter} (HIGHEST_PRECEDENCE) so a rejected request still carries a
 * trace id in its log line.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String PROTECTED_PATH_PREFIX = "/accounts";

    private final byte[] expectedApiKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(@Value("${internal.api-key}") String apiKey, ObjectMapper objectMapper) {
        this.expectedApiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the business APIs are gated; /health, /h2-console, etc. stay open.
        return !request.getRequestURI().startsWith(PROTECTED_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!isValid(providedKey)) {
            log.warn("Rejected unauthenticated request to {} (missing/invalid {})",
                    request.getRequestURI(), API_KEY_HEADER);
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isValid(String providedKey) {
        if (providedKey == null) {
            return false;
        }
        // Constant-time comparison so a wrong key can't be guessed from response timing.
        return MessageDigest.isEqual(providedKey.getBytes(StandardCharsets.UTF_8), expectedApiKey);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Missing or invalid API key",
                null);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
