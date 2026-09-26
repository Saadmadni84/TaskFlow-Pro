package com.taskflow.taskflow.criticalpath.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Pure domain result containing project-level Critical Path Analysis metrics.
 */
public record CriticalPathResult(
        LocalDate projectStartDate,
        LocalDate projectCompletionDate,
        List<UUID> criticalTaskIds,
        List<List<UUID>> criticalPaths,
        List<TaskScheduleMetrics> taskMetrics,
        boolean isTruncated
) {
    public CriticalPathResult {
        criticalTaskIds = criticalTaskIds != null ? List.copyOf(criticalTaskIds) : Collections.emptyList();
        criticalPaths = criticalPaths != null ? List.copyOf(criticalPaths) : Collections.emptyList();
        taskMetrics = taskMetrics != null ? List.copyOf(taskMetrics) : Collections.emptyList();
    }

    public static CriticalPathResult empty() {
        return new CriticalPathResult(null, null, List.of(), List.of(), List.of(), false);
    }
}
