package com.taskflow.taskflow.common.exception;

import java.util.UUID;

public class TaskNotFoundException extends ResourceNotFoundException {

    public TaskNotFoundException(UUID taskId) {
        super(String.format("Task not found with identifier: %s", taskId), "TASK_NOT_FOUND");
    }

    public TaskNotFoundException(String message) {
        super(message, "TASK_NOT_FOUND");
    }
}
