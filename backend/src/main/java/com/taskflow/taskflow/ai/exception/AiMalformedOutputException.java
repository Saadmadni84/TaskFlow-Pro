package com.taskflow.taskflow.ai.exception;

import com.taskflow.taskflow.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an AI provider returns unparseable or schema-violating JSON output.
 */
public class AiMalformedOutputException extends ApiException {

    public AiMalformedOutputException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, "AI_MALFORMED_OUTPUT");
    }

    public AiMalformedOutputException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, "AI_MALFORMED_OUTPUT");
        if (cause != null) {
            initCause(cause);
        }
    }
}
