package com.taskflow.taskflow.scheduling.dto;

/**
 * Summary metrics of the downstream impact of a proposed schedule modification.
 */
public record ScheduleImpactSummaryDto(
        int affectedTaskCount,
        int changedTaskCount,
        int unchangedTaskCount,
        int maximumDelayDays
) {
}
