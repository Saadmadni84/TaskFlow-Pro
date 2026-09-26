package com.taskflow.taskflow.ai.ratelimit;

import com.taskflow.taskflow.ai.exception.AiRateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lightweight, in-memory sliding window rate limiter designed for the AI suggestion endpoint.
 *
 * <p><strong>Note:</strong> This rate limiter is instance-local and maintains state in memory.
 * When horizontally scaling TaskFlow Pro across multiple container instances, this component
 * should be replaced with distributed rate limiting (e.g., Redis token bucket).
 */
@Component
public class AiRateLimiter {

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Instant>> requestLog = new ConcurrentHashMap<>();

    public AiRateLimiter(@Value("${ai.rate-limit.requests-per-minute:30}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    /**
     * Checks whether the client identified by {@code clientKey} is permitted to execute an AI request.
     * Throws {@link AiRateLimitExceededException} if the quota is exceeded.
     *
     * @param clientKey client identifier (such as IP address or tenant ID)
     */
    public void checkLimit(String clientKey) {
        if (maxRequestsPerMinute <= 0) {
            return; // Rate limiting disabled
        }

        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minusSeconds(60);

        ConcurrentLinkedQueue<Instant> timestamps = requestLog.computeIfAbsent(clientKey, k -> new ConcurrentLinkedQueue<>());

        // Evict expired timestamps outside the 60-second sliding window
        while (!timestamps.isEmpty() && timestamps.peek().isBefore(oneMinuteAgo)) {
            timestamps.poll();
        }

        if (timestamps.size() >= maxRequestsPerMinute) {
            throw new AiRateLimitExceededException(
                    "AI dependency suggestion rate limit exceeded (" + maxRequestsPerMinute + " requests/minute). Please wait before trying again."
            );
        }

        timestamps.add(now);
    }
}
