package com.taskflow.taskflow.dependency.graph;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Value object representing the subset of tasks affected downstream by a change to rootTask.
 *
 * Guarantees:
 * 1. affectedTaskIds contains all downstream descendants without duplicates.
 * 2. topologicalOrder orders all affected descendants such that every predecessor within the
 *    subgraph comes before its successors (crucial for future schedule recalculation).
 */
public record AffectedSubgraph(
        UUID rootTaskId,
        Set<UUID> affectedTaskIds,
        List<UUID> topologicalOrder
) {
    public AffectedSubgraph {
        Objects.requireNonNull(rootTaskId, "Root task ID must not be null");
        affectedTaskIds = affectedTaskIds != null ? Set.copyOf(affectedTaskIds) : Collections.emptySet();
        topologicalOrder = topologicalOrder != null ? List.copyOf(topologicalOrder) : Collections.emptyList();
    }

    public boolean hasAffectedTasks() {
        return !affectedTaskIds.isEmpty();
    }

    public int getAffectedCount() {
        return affectedTaskIds.size();
    }
}
