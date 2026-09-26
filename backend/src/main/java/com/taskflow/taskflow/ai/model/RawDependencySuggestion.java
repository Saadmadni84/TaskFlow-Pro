package com.taskflow.taskflow.ai.model;

import java.util.UUID;

/**
 * Raw suggestion emitted by the LLM provider prior to server-side validation.
 */
public record RawDependencySuggestion(
        UUID predecessorTaskId,
        UUID successorTaskId,
        Double confidence,
        String reason
) {
}
