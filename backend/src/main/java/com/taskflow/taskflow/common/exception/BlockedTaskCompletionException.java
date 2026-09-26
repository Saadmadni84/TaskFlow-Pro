package com.taskflow.taskflow.common.exception;

import org.springframework.http.HttpStatus;

public class BlockedTaskCompletionException extends ApiException {

    public BlockedTaskCompletionException(String message) {
        super(message, HttpStatus.CONFLICT, "TASK_BLOCKED");
    }
}
