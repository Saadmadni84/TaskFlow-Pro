package com.taskflow.taskflow.dependency.dto;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Visual dependency graph projection for a project.
 */
public record DependencyGraphDto(
        UUID projectId,
        List<DependencyGraphNodeDto> nodes,
        List<DependencyGraphEdgeDto> edges
) {
    public DependencyGraphDto {
        nodes = nodes != null ? List.copyOf(nodes) : Collections.emptyList();
        edges = edges != null ? List.copyOf(edges) : Collections.emptyList();
    }
}
