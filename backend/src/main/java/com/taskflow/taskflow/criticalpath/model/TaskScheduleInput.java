package com.taskflow.taskflow.criticalpath.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain input for Critical Path Analysis representing a task's schedule parameters.
 * Decoupled from JPA entities, HTTP requests, and Spring dependencies.
 */
public record TaskScheduleInput(
        UUID taskId,
        String title,
        LocalDate scheduledStartDate,
        LocalDate scheduledDueDate,
        LocalDate plannedStartDate,
        int durationDays
) {
    public TaskScheduleInput {
        Objects.requireNonNull(taskId, "Task ID must not be null");
        if (durationDays < 1) {
            throw new IllegalArgumentException("durationDays must be at least 1, but was: " + durationDays);
        }
        title = title != null ? title : "";
    }

    /**
     * Determines the base start date for forward pass initialization.
     * Prefers dependency-aware scheduledStartDate, falls back to user plannedStartDate.
     */
    public LocalDate getEffectiveBaseStartDate() {
        return scheduledStartDate != null ? scheduledStartDate : plannedStartDate;
    }
}
