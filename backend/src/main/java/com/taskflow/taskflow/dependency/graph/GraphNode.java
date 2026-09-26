package com.taskflow.taskflow.dependency.graph;

import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight immutable representation of a node in the dependency graph.
 */
public record GraphNode(UUID taskId) {

    public GraphNode {
        Objects.requireNonNull(taskId, "Task ID must not be null");
    }
}
