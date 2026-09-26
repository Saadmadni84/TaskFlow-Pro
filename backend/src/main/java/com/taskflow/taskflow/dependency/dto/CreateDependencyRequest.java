package com.taskflow.taskflow.dependency.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDependencyRequest(
        @NotNull(message = "Predecessor task ID is required")
        UUID predecessorTaskId,

        @NotNull(message = "Successor task ID is required")
        UUID successorTaskId
) {}
