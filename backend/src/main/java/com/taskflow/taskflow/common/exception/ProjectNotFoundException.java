package com.taskflow.taskflow.common.exception;

import java.util.UUID;

public class ProjectNotFoundException extends ResourceNotFoundException {

    public ProjectNotFoundException(UUID projectId) {
        super(String.format("Project not found with identifier: %s", projectId), "PROJECT_NOT_FOUND");
    }

    public ProjectNotFoundException(String message) {
        super(message, "PROJECT_NOT_FOUND");
    }
}
