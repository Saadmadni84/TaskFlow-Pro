package com.taskflow.taskflow.scheduling.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.taskflow.taskflow.scheduling.model.ImpactType;
import com.taskflow.taskflow.scheduling.model.ReasonType;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Detailed schedule comparison and explanation for an individual task in an impact preview.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskScheduleImpactDto(
        UUID taskId,
        String title,
        LocalDate currentPlannedStart,
        LocalDate proposedPlannedStart,
        LocalDate currentScheduledStart,
        LocalDate proposedScheduledStart,
        LocalDate currentScheduledDue,
        LocalDate proposedScheduledDue,
        int durationDays,
        int startShiftDays,
        int dueShiftDays,
        int shiftDays,
        ImpactType impactType,
        ReasonType reasonType,
        List<UUID> constraintSourceTaskIds,
        LocalDate constraintDate,
        String reason
) {
    public TaskScheduleImpactDto {
        constraintSourceTaskIds = constraintSourceTaskIds != null
                ? List.copyOf(constraintSourceTaskIds)
                : Collections.emptyList();
    }
}
