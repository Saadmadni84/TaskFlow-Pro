package com.taskflow.taskflow.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SelfDependencyException extends ApiException {

    public SelfDependencyException(UUID taskId) {
        super(String.format("Self-dependency is forbidden: task [%s] cannot depend on itself", taskId),
                HttpStatus.BAD_REQUEST,
                "SELF_DEPENDENCY");
    }

    public SelfDependencyException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "SELF_DEPENDENCY");
    }
}
