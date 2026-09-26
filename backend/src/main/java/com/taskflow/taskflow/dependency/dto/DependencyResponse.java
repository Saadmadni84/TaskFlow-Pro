package com.taskflow.taskflow.dependency.dto;

import java.time.Instant;
import java.util.UUID;

public record DependencyResponse(
        UUID id,
        UUID predecessorTaskId,
        UUID successorTaskId,
        Instant createdAt
) {}
