package com.taskflow.taskflow.ai.dto;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Root response payload containing filtered, actionable dependency suggestions for a target task.
 */
public record DependencySuggestionResponse(
        UUID targetTaskId,
        int suggestionCount,
        List<DependencySuggestionDto> suggestions
) {
    public DependencySuggestionResponse {
        suggestions = suggestions != null ? List.copyOf(suggestions) : Collections.emptyList();
    }
}
