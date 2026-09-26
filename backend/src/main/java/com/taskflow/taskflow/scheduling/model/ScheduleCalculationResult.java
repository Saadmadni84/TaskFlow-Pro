package com.taskflow.taskflow.scheduling.model;

import java.time.LocalDate;

/**
 * Immutable result of a deterministic constraint-based schedule calculation.
 *
 * @param plannedStartDate the user's independent planned start date
 * @param scheduledStartDate the calculated scheduled start date respecting all predecessor constraints
 * @param scheduledDueDate the calculated scheduled due date (scheduledStartDate + durationDays - 1)
 * @param durationDays calendar days duration (strictly preserved)
 * @param latestPredecessorConstraint maximum required start from predecessors (max(pred.scheduledDueDate + 1 day)), or null if no predecessors
 */
public record ScheduleCalculationResult(
        LocalDate plannedStartDate,
        LocalDate scheduledStartDate,
        LocalDate scheduledDueDate,
        int durationDays,
        LocalDate latestPredecessorConstraint
) {
    /**
     * Returns true if the task was shifted later than its independent planned start date
     * due to prerequisite constraints.
     */
    public boolean hasShift() {
        return plannedStartDate != null && scheduledStartDate != null && !plannedStartDate.equals(scheduledStartDate);
    }
}
