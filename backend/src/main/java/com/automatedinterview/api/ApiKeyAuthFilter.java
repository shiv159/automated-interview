package com.automatedinterview.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Protects all /api/v1/question-bank/** routes with an API key.
 * Callers must supply the correct value in the X-Api-Key request header.
 * Returns 401 with a JSON body on any mismatch.
 */
@Component
@Order(1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String HEADER = "X-Api-Key";
    private static final String PROTECTED_PREFIX = "/api/v1/question-bank";

    private final String expectedKey;

    public ApiKeyAuthFilter(@Value("${app.question-bank.api-key:}") String apiKey) {
        this.expectedKey = apiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (expectedKey == null || expectedKey.isBlank()) {
            log.error("QUESTION_BANK_API_KEY is not configured on server.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        String provided = request.getHeader(HEADER);
        if (!expectedKey.equals(provided)) {
            log.warn("question_bank_auth_rejected method={} path={} ip={}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}