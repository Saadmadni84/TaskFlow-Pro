package com.taskflow.taskflow.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.taskflow.ai.config.AiProperties;
import com.taskflow.taskflow.ai.exception.AiMalformedOutputException;
import com.taskflow.taskflow.ai.model.DependencyCandidateTask;
import com.taskflow.taskflow.ai.model.DependencySuggestionContext;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;
import com.taskflow.taskflow.ai.prompt.DependencySuggestionPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiDependencySuggestionProviderTest {

    private AiProperties aiProperties;
    private DependencySuggestionPromptBuilder promptBuilder;
    private ObjectMapper objectMapper;
    private GeminiDependencySuggestionProvider provider;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        promptBuilder = new DependencySuggestionPromptBuilder();
        objectMapper = new ObjectMapper();
        provider = new GeminiDependencySuggestionProvider(aiProperties, promptBuilder, objectMapper);
    }

    @Test
    @DisplayName("Should return empty result when AI is disabled")
    void shouldReturnEmptyWhenAiDisabled() {
        aiProperties.setEnabled(false);

        DependencySuggestionContext context = new DependencySuggestionContext(
                new DependencyCandidateTask(UUID.randomUUID(), "Task A", "Desc", "BACKLOG"),
                List.of(),
                List.of(),
                5
        );

        DependencySuggestionResult result = provider.suggest(context);
        assertThat(result).isNotNull();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Provider name should be gemini")
    void shouldReturnCorrectProviderName() {
        assertThat(provider.getProviderName()).isEqualTo("gemini");
    }
}
