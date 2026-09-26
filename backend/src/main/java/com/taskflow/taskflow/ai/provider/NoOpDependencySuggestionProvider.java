package com.taskflow.taskflow.ai.provider;

import com.taskflow.taskflow.ai.model.DependencySuggestionContext;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback/offline provider returning empty suggestions.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "noop")
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
