package com.taskflow.taskflow.dependency.graph;

import java.util.Objects;
import java.util.UUID;

/**
 * Directed edge representing predecessor -> successor relationship.
 */
public record GraphEdge(UUID predecessor, UUID successor) {

    public GraphEdge {
        Objects.requireNonNull(predecessor, "Predecessor task ID must not be null");
        Objects.requireNonNull(successor, "Successor task ID must not be null");
        if (predecessor.equals(successor)) {
            throw new IllegalArgumentException("Self-dependency is forbidden: predecessor cannot equal successor");
        }
    }
}
