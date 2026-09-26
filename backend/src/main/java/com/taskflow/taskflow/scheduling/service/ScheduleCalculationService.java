package com.taskflow.taskflow.scheduling.service;

import com.taskflow.taskflow.scheduling.model.ScheduleCalculationResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Pure deterministic calculation service for task schedules.
 *
 * Authoritative Rules:
 * 1. durationDays is the number of calendar days occupied by the task (durationDays >= 1).
 * 2. dueDate = startDate + durationDays - 1 day.
 * 3. Successor constraint: successor.scheduledStartDate >= predecessor.scheduledDueDate + 1 day.
 * 4. For multiple predecessors P1, P2, ... Pn:
 *    latestPredecessorConstraint = max(Pi.scheduledDueDate + 1 day).
 * 5. scheduledStartDate = max(plannedStartDate, latestPredecessorConstraint).
 * 6. scheduledDueDate = scheduledStartDate + durationDays - 1 day.
 * 7. Duration is strictly preserved.
 * 8. Delay never compounds in converging graphs.
 */
@Service
public class ScheduleCalculationService {

    /**
     * Calculates the deterministic schedule for a task given its planned start, duration,
     * and the collection of predecessor scheduled due dates.
     *
     * @param plannedStartDate the user's independent planned start date
     * @param durationDays calendar days duration (must be >= 1)
     * @param predecessorDueDates collection of scheduled due dates of all direct predecessors
     * @return the calculation result
     */
    public ScheduleCalculationResult calculateSchedule(
            LocalDate plannedStartDate,
            int durationDays,
            Collection<LocalDate> predecessorDueDates
    ) {
        if (durationDays < 1) {
            throw new IllegalArgumentException("durationDays must be at least 1, but was: " + durationDays);
        }

        LocalDate latestConstraint = null;

        if (predecessorDueDates != null && !predecessorDueDates.isEmpty()) {
            for (LocalDate predDue : predecessorDueDates) {
                if (predDue != null) {
                    LocalDate requiredStart = predDue.plusDays(1);
                    if (latestConstraint == null || requiredStart.isAfter(latestConstraint)) {
                        latestConstraint = requiredStart;
                    }
                }
            }
        }

        LocalDate scheduledStart;
        if (latestConstraint == null) {
            scheduledStart = plannedStartDate;
        } else if (plannedStartDate == null) {
            scheduledStart = latestConstraint;
        } else {
            scheduledStart = plannedStartDate.isAfter(latestConstraint) ? plannedStartDate : latestConstraint;
        }

        LocalDate scheduledDue = null;
        if (scheduledStart != null) {
            scheduledDue = scheduledStart.plusDays(durationDays - 1);
        }

        return new ScheduleCalculationResult(
                plannedStartDate,
                scheduledStart,
                scheduledDue,
                durationDays,
                latestConstraint
        );
    }
}
