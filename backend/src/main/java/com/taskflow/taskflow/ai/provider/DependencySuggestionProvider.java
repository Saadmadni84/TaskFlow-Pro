package com.taskflow.taskflow.ai.provider;

import com.taskflow.taskflow.ai.model.DependencySuggestionContext;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;

/**
 * Strategy interface abstracting LLM vendors and AI backends for dependency suggestion.
 *
 * Decouples domain logic from specific model providers and enables deterministic offline testing.
 */
public interface DependencySuggestionProvider {

    /**
     * Generates raw dependency suggestions based on grounded project context.
     *
     * @param context grounded target task, candidate tasks, and existing edges
     * @return raw suggestions for server-side validation
     */
    DependencySuggestionResult suggest(DependencySuggestionContext context);

    /**
     * Returns the identifier of this provider implementation (e.g. "gemini", "mock", "noop").
     */
    String getProviderName();
}
