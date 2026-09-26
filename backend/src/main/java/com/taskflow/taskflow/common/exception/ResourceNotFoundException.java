package com.taskflow.taskflow.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceName, identifier),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public static ResourceNotFoundException withCustomCode(String message, String errorCode) {
        return new ResourceNotFoundException(message, errorCode, null);
    }

    private ResourceNotFoundException(String message, String errorCode, Void unused) {
        super(message, HttpStatus.NOT_FOUND, errorCode);
    }
}
