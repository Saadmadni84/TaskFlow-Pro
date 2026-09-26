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

        String description,

        TaskStatus workflowStatus,

        LocalDate startDate,

        LocalDate dueDate,

        @Min(value = 0, message = "Duration days cannot be negative")
        Integer durationDays
) {}
