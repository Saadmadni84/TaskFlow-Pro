package com.taskflow.taskflow.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
        MDC.clear();
    }

    @Test
    @DisplayName("Propagates and validates valid incoming X-Request-Id header")
    void shouldPropagateValidIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req-valid-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuringFilter = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringFilter.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("req-valid-12345");
        assertThat(mdcDuringFilter.get()).isEqualTo("req-valid-12345");
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)).isNull(); // Cleared in finally
    }

    @Test
    @DisplayName("Generates new UUID if incoming X-Request-Id is missing or empty")
    void shouldGenerateRequestIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuringFilter = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringFilter.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY));

        filter.doFilter(request, response, chain);

        String generatedId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(generatedId).isNotBlank();
        assertThat(mdcDuringFilter.get()).isEqualTo(generatedId);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Sanitizes and replaces invalid/unsafe incoming X-Request-Id with generated UUID")
    void shouldSanitizeUnsafeRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "malicious<script>alert(1)</script> \r\n");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuringFilter = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringFilter.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY));

        filter.doFilter(request, response, chain);

        String safeId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(safeId).isNotBlank();
        assertThat(safeId).doesNotContain("<script>");
        assertThat(mdcDuringFilter.get()).isEqualTo(safeId);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)).isNull();
    }
}
