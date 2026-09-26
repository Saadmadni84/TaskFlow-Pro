package com.taskflow.taskflow.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTaskException extends ApiException {

    public InvalidTaskException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_TASK");
    }

    public InvalidTaskException(String message, String errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
}
