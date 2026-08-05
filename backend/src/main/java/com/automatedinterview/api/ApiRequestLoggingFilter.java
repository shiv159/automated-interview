package com.automatedinterview.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Logs the outcome of every API request without recording sensitive payloads. */
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_MDC_KEY = "correlationId";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = safeCorrelationId(request.getHeader(CORRELATION_HEADER));
        long started = System.nanoTime();
        response.setHeader(CORRELATION_HEADER, correlationId);
        MDC.put(CORRELATION_MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            log.info("api_request method={} path={} status={} durationMs={} correlationId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, correlationId);
            MDC.remove(CORRELATION_MDC_KEY);
        }
    }

    private static String safeCorrelationId(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9._-]{1,80}")) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
