package com.taskflow.taskflow.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateDependencyException extends ApiException {

    public DuplicateDependencyException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_DEPENDENCY");
    }
}
