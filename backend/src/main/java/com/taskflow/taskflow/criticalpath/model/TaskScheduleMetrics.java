package com.taskflow.taskflow.criticalpath.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain model containing Critical Path Method (CPM) schedule metrics for a single task.
 */
public record TaskScheduleMetrics(
        UUID taskId,
        String title,
        int durationDays,
        LocalDate earliestStart,
        LocalDate earliestFinish,
        LocalDate latestStart,
        LocalDate latestFinish,
        long totalSlackDays,
        boolean isCritical
) {
    public TaskScheduleMetrics {
        Objects.requireNonNull(taskId, "Task ID must not be null");
        Objects.requireNonNull(earliestStart, "earliestStart must not be null");
        Objects.requireNonNull(earliestFinish, "earliestFinish must not be null");
        Objects.requireNonNull(latestStart, "latestStart must not be null");
        Objects.requireNonNull(latestFinish, "latestFinish must not be null");
        title = title != null ? title : "";
    }
}
