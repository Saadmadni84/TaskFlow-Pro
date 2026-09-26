package com.taskflow.taskflow.task.dto;

import com.taskflow.taskflow.task.entity.TaskStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request to update an existing task.
 * Note: dependencyStatus is NOT client-controllable and can only be updated by the DAG engine.
 */
public record UpdateTaskRequest(
        @NotBlank(message = "Task title is required")
        @Size(max = 255, message = "Task title must not exceed 255 characters")
        String title,

        @Size(max = 4000, message = "Task description must not exceed 4000 characters")
        String description,

        TaskStatus workflowStatus,

        LocalDate plannedStartDate,

        LocalDate startDate,

        LocalDate dueDate,

        @Min(value = 0, message = "Duration days cannot be negative")
        @jakarta.validation.constraints.Max(value = 3650, message = "Duration days cannot exceed 3650")
        Integer durationDays
) {
    public UpdateTaskRequest(
            String title,
            String description,
            TaskStatus workflowStatus,
            LocalDate startDate,
            LocalDate dueDate,
            Integer durationDays
    ) {
        this(title, description, workflowStatus, startDate, startDate, dueDate, durationDays);
    }

    public LocalDate resolvePlannedStart() {
        return plannedStartDate != null ? plannedStartDate : startDate;
    }
}
