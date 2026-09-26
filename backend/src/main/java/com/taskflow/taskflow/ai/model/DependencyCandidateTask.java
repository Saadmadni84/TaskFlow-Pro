package com.taskflow.taskflow.ai.model;

import java.util.UUID;

/**
 * Minimal task context passed into the AI prompt to ground dependency suggestions.
 */
public record DependencyCandidateTask(
        UUID id,
        String title,
        String description,
        String workflowStatus
) {
}
