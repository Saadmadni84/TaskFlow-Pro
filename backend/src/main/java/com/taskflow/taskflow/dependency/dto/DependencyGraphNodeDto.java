package com.taskflow.taskflow.dependency.dto;

import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.TaskStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Node representation designed specifically for visual DAG rendering.
 */
public record DependencyGraphNodeDto(
        UUID id,
        String title,
        TaskStatus workflowStatus,
        DependencyStatus dependencyStatus,
        LocalDate scheduledStartDate,
        LocalDate scheduledDueDate,
        LocalDate plannedStartDate,
        Integer durationDays
) {}
