package com.taskflow.taskflow.ai.model;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Bounded project context supplied to an AI dependency suggestion provider.
 */
public record DependencySuggestionContext(
        DependencyCandidateTask targetTask,
        List<DependencyCandidateTask> candidateTasks,
        List<ExistingEdge> existingDependencies,
        int maxSuggestions
) {
    public DependencySuggestionContext {
        candidateTasks = candidateTasks != null ? List.copyOf(candidateTasks) : Collections.emptyList();
        existingDependencies = existingDependencies != null ? List.copyOf(existingDependencies) : Collections.emptyList();
    }

    public record ExistingEdge(UUID predecessorId, UUID successorId) {}
}
