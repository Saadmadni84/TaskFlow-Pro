package com.taskflow.taskflow.ai.exception;

import com.taskflow.taskflow.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an AI suggestion request is received while the AI engine is disabled via configuration.
 */
public class AiDisabledException extends ApiException {

    public AiDisabledException() {
        super("AI dependency suggestions are currently disabled", HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED");
    }

    public AiDisabledException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED");
    }
}
