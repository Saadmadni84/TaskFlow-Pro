package com.taskflow.taskflow.ai.provider;

import com.taskflow.taskflow.ai.model.DependencySuggestionContext;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;

/**
 * Fallback/offline provider returning empty suggestions.
 */
public class NoOpDependencySuggestionProvider implements DependencySuggestionProvider {

    @Override
    public DependencySuggestionResult suggest(DependencySuggestionContext context) {
        return DependencySuggestionResult.empty();
    }

    @Override
    public String getProviderName() {
        return "noop";
    }
}
