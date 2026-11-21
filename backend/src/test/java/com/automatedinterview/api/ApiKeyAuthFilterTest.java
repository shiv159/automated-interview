package com.automatedinterview.api;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyAuthFilterTest {

    @Test
    void allowsAccessWithValidKey() throws Exception {
        var filter = new ApiKeyAuthFilter("secret-123");
        var request = new MockHttpServletRequest("GET", "/api/v1/question-bank/questions");
        request.addHeader("X-Api-Key", "secret-123");
        var response = new MockHttpServletResponse();
        var chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertTrue(chained.get());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void rejectsAccessWithInvalidKey() throws Exception {
        var filter = new ApiKeyAuthFilter("secret-123");
        var request = new MockHttpServletRequest("GET", "/api/v1/question-bank/questions");
        request.addHeader("X-Api-Key", "wrong-key");
        var response = new MockHttpServletResponse();
        var chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertFalse(chained.get());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void bypassesUnprotectedRoute() throws Exception {
        var filter = new ApiKeyAuthFilter("secret-123");
        var request = new MockHttpServletRequest("GET", "/api/v1/sessions");
        var response = new MockHttpServletResponse();
        var chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertTrue(chained.get());
    }
}
