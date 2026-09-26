package com.taskflow.taskflow.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * Result returned by a {@link com.taskflow.taskflow.ai.provider.DependencySuggestionProvider}.
 */
public record DependencySuggestionResult(
        List<RawDependencySuggestion> suggestions
) {
    public DependencySuggestionResult {
        suggestions = suggestions != null ? List.copyOf(suggestions) : Collections.emptyList();
    }

    public static DependencySuggestionResult empty() {
        return new DependencySuggestionResult(Collections.emptyList());
    }
}
