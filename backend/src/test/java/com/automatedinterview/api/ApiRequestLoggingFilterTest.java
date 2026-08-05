package com.automatedinterview.api;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiRequestLoggingFilterTest {
    @Test
    void addsCorrelationHeaderAndClearsMdc() throws Exception {
        var filter = new ApiRequestLoggingFilter();
        var request = new MockHttpServletRequest("GET", "/api/health");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_OK));

        assertNotNull(response.getHeader(ApiRequestLoggingFilter.CORRELATION_HEADER));
        assertNull(MDC.get(ApiRequestLoggingFilter.CORRELATION_MDC_KEY));
    }
}
