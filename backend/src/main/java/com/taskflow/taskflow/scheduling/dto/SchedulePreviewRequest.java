package com.taskflow.taskflow.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Request payload representing a proposed schedule modification to evaluate.
 */
public record SchedulePreviewRequest(
        @NotNull(message = "Planned start date is required")
        LocalDate plannedStartDate
) {
}
