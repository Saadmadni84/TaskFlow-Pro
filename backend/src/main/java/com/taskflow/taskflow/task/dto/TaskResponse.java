package com.taskflow.taskflow.task.dto;

import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        TaskStatus workflowStatus,
        DependencyStatus dependencyStatus,
        LocalDate startDate,
        LocalDate dueDate,
        Integer durationDays,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
