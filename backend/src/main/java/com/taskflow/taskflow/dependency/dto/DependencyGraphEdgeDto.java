package com.taskflow.taskflow.dependency.dto;

import java.util.UUID;

/**
 * Directed edge representation for visual DAG rendering.
 * predecessorTaskId -> successorTaskId (predecessor is prerequisite of successor).
 */
public record DependencyGraphEdgeDto(
        UUID id,
        UUID predecessorTaskId,
        UUID successorTaskId
) {}
