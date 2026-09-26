package com.taskflow.taskflow.ai.exception;

import com.taskflow.taskflow.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when requests to the AI dependency suggestion endpoint exceed instance rate limits.
 */
public class AiRateLimitExceededException extends ApiException {

    public AiRateLimitExceededException() {
        super("Too many AI suggestion requests. Please try again shortly.", HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
    }

    public AiRateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
    }
}
