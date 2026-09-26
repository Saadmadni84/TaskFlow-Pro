package com.taskflow.taskflow.common.exception;

import org.springframework.http.HttpStatus;

public class DomainException extends ApiException {

    public DomainException(String message, String errorCode) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
    }

    public DomainException(String message, HttpStatus status, String errorCode) {
        super(message, status, errorCode);
    }
}
