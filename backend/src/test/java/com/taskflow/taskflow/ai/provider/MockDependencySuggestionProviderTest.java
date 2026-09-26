package com.taskflow.taskflow.ai.provider;

import com.taskflow.taskflow.ai.model.DependencyCandidateTask;
import com.taskflow.taskflow.ai.model.DependencySuggestionContext;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockDependencySuggestionProviderTest {

    private MockDependencySuggestionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockDependencySuggestionProvider();
    }

    @Test
    @DisplayName("suggest identifies lifecycle prerequisites (Database -> API)")
    void shouldSuggestPrerequisitesByLifecycle() {
        UUID schemaTaskId = UUID.randomUUID();
        UUID apiTaskId = UUID.randomUUID();

        DependencyCandidateTask target = new DependencyCandidateTask(
                apiTaskId,
                "Implement Backend REST API",
                "Create API controllers and endpoints",
                "BACKLOG"
        );

        DependencyCandidateTask candidate = new DependencyCandidateTask(
                schemaTaskId,
                "Design Database Schema",
                "PostgreSQL tables and migrations",
                "BACKLOG"
        );

        DependencySuggestionContext context = new DependencySuggestionContext(
                target,
                List.of(candidate),
                List.of(),
                5
        );

        DependencySuggestionResult result = provider.suggest(context);

        assertThat(result).isNotNull();
        assertThat(result.suggestions()).hasSize(1);
        var suggestion = result.suggestions().get(0);
        assertThat(suggestion.predecessorTaskId()).isEqualTo(schemaTaskId);
        assertThat(suggestion.successorTaskId()).isEqualTo(apiTaskId);
        assertThat(suggestion.confidence()).isGreaterThanOrEqualTo(0.8);
        assertThat(suggestion.reason()).contains("database");
    }

    @Test
    @DisplayName("suggest skips existing edges")
    void shouldSkipExistingEdges() {
        UUID schemaTaskId = UUID.randomUUID();
        UUID apiTaskId = UUID.randomUUID();

        DependencyCandidateTask target = new DependencyCandidateTask(
                apiTaskId,
                "Implement Backend API",
                "Endpoints",
                "BACKLOG"
        );

        DependencyCandidateTask candidate = new DependencyCandidateTask(
                schemaTaskId,
                "Design Database",
                "Tables",
                "BACKLOG"
        );

        DependencySuggestionContext context = new DependencySuggestionContext(
                target,
                List.of(candidate),
                List.of(new DependencySuggestionContext.ExistingEdge(schemaTaskId, apiTaskId)),
                5
        );

        DependencySuggestionResult result = provider.suggest(context);
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("suggest returns empty when no candidates available")
    void shouldReturnEmptyWhenNoCandidates() {
        DependencyCandidateTask target = new DependencyCandidateTask(
                UUID.randomUUID(),
                "Implement Backend API",
                "Endpoints",
                "BACKLOG"
        );

        DependencySuggestionContext context = new DependencySuggestionContext(
                target,
                List.of(),
                List.of(),
                5
        );

        DependencySuggestionResult result = provider.suggest(context);
        assertThat(result.suggestions()).isEmpty();
    }
}
