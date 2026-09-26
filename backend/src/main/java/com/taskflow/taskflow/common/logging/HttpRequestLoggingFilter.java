package com.taskflow.taskflow.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lightweight HTTP request logging filter.
 * Records HTTP method, request path, response status, duration in ms, and correlation requestId.
 * Differentiates 4xx (WARN) from 5xx (ERROR) and 2xx/3xx (INFO).
 * Strictly avoids logging request bodies, cookies, authorization tokens, or passwords.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String path = request.getRequestURI();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY);

            // Avoid log spamming for high-frequency internal actuator health probes
            if (path.startsWith("/actuator/health")) {
                if (log.isDebugEnabled()) {
                    log.debug("HTTP {} {} status={} durationMs={} requestId={}", method, path, status, durationMs, requestId);
                }
            } else if (status >= 500) {
                log.error("HTTP {} {} status={} durationMs={} requestId={}", method, path, status, durationMs, requestId);
            } else if (status >= 400) {
                log.warn("HTTP {} {} status={} durationMs={} requestId={}", method, path, status, durationMs, requestId);
            } else {
                log.info("HTTP {} {} status={} durationMs={} requestId={}", method, path, status, durationMs, requestId);
            }
        }
    }
}
