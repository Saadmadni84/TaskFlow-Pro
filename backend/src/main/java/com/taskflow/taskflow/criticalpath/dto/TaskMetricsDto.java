package com.taskflow.taskflow.criticalpath.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO exposing schedule metrics and critical status for an individual task.
 */
public record TaskMetricsDto(
        UUID taskId,
        String title,
        int durationDays,
        LocalDate earliestStart,
        LocalDate earliestFinish,
        LocalDate latestStart,
        LocalDate latestFinish,
        long totalSlackDays,
        boolean isCritical
) {}
