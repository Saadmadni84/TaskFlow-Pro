package com.taskflow.taskflow.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidDependencyException extends ApiException {

    public InvalidDependencyException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DEPENDENCY");
    }

    public InvalidDependencyException(String message, String errorCode) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
    }
}
