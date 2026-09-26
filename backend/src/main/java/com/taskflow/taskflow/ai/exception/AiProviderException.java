package com.taskflow.taskflow.ai.exception;

import com.taskflow.taskflow.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an external AI provider encounters a failure, timeout, network error, or rate limit.
 */
public class AiProviderException extends ApiException {

    public AiProviderException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE");
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE");
        if (cause != null) {
            initCause(cause);
        }
    }
}
