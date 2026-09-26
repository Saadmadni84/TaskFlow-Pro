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
        List<TaskMetricsDto> tasks,
        Long projectDurationDays,
        Integer criticalTaskCount,
        Integer criticalPathCount
) {
    public CriticalPathResponse(
            UUID projectId,
            LocalDate projectStartDate,
            LocalDate projectCompletionDate,
            List<UUID> criticalTaskIds,
            List<List<UUID>> criticalPaths,
            List<TaskMetricsDto> tasks
    ) {
        this(
                projectId,
                projectStartDate,
                projectCompletionDate,
                criticalTaskIds,
                criticalPaths,
                tasks,
                (projectStartDate != null && projectCompletionDate != null)
                        ? java.time.temporal.ChronoUnit.DAYS.between(projectStartDate, projectCompletionDate) + 1
                        : 0L,
                criticalTaskIds != null ? criticalTaskIds.size() : 0,
                criticalPaths != null ? criticalPaths.size() : 0
        );
    }

    public CriticalPathResponse {
        criticalTaskIds = criticalTaskIds != null ? List.copyOf(criticalTaskIds) : Collections.emptyList();
        criticalPaths = criticalPaths != null ? List.copyOf(criticalPaths) : Collections.emptyList();
        tasks = tasks != null ? List.copyOf(tasks) : Collections.emptyList();
        if (projectDurationDays == null) {
            projectDurationDays = (projectStartDate != null && projectCompletionDate != null)
                    ? java.time.temporal.ChronoUnit.DAYS.between(projectStartDate, projectCompletionDate) + 1
                    : 0L;
        }
        if (criticalTaskCount == null) {
            criticalTaskCount = criticalTaskIds.size();
        }
        if (criticalPathCount == null) {
            criticalPathCount = criticalPaths.size();
        }
    }
}
