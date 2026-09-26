package com.taskflow.taskflow.scheduling.model;

/**
 * Machine-readable categorization of the reason for a task's schedule outcome in the impact preview.
 */
public enum ReasonType {
    /**
     * Schedule changed directly due to user input on this source task.
     */
    SOURCE_TASK_CHANGE,

    /**
     * Schedule is bound by one or more predecessor dependency constraints.
     */
    DEPENDENCY_CONSTRAINT,

    /**
     * Task's independent planned schedule is later than or equal to all predecessor constraints,
     * so predecessor dates do not drive the schedule.
     */
    PLANNED_DATE_DOMINANT,

    /**
     * No shift occurred and no constraints altered the scheduled dates.
     */
    NO_CHANGE
}
