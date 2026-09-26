package com.taskflow.taskflow.ai.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request payload to explicitly accept and persist a proposed dependency relationship.
 */
public record AcceptSuggestionRequest(
        @NotNull(message = "Predecessor task ID is required")
        UUID predecessorTaskId,

        @NotNull(message = "Successor task ID is required")
        UUID successorTaskId
) {
}
