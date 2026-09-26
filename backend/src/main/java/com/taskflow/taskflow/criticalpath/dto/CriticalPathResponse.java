package com.taskflow.taskflow.criticalpath.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Top-level REST response for project Critical Path Analysis.
 */
public record CriticalPathResponse(
        UUID projectId,
        LocalDate projectStartDate,
        LocalDate projectCompletionDate,
        List<UUID> criticalTaskIds,
        List<List<UUID>> criticalPaths,
        List<TaskMetricsDto> tasks
) {
    public CriticalPathResponse {
        criticalTaskIds = criticalTaskIds != null ? List.copyOf(criticalTaskIds) : Collections.emptyList();
        criticalPaths = criticalPaths != null ? List.copyOf(criticalPaths) : Collections.emptyList();
        tasks = tasks != null ? List.copyOf(tasks) : Collections.emptyList();
    }
}
