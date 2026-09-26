package com.taskflow.taskflow.scheduling.dto;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Root response payload for a dependency impact preview.
 */
public record ScheduleImpactPreviewResponse(
        UUID sourceTaskId,
        ScheduleImpactSummaryDto summary,
        List<TaskScheduleImpactDto> tasks
) {
    public ScheduleImpactPreviewResponse {
        tasks = tasks != null ? List.copyOf(tasks) : Collections.emptyList();
    }

    /**
     * Alias for tasks list to support semantic domain terminology.
     */
    public List<TaskScheduleImpactDto> affectedTasks() {
        return tasks;
    }
}
