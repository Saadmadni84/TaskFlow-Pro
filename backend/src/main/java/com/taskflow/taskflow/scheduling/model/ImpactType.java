package com.taskflow.taskflow.scheduling.model;

/**
 * Categorizes the scheduling impact of a proposed change on a task.
 */
public enum ImpactType {
    /**
     * The task's scheduled dates do not change.
     */
    UNCHANGED,

    /**
     * The task's scheduled dates are shifted later (e.g. for the directly edited source task).
     */
    DELAYED,

    /**
     * The task's scheduled dates are shifted earlier (e.g. when upstream constraints are relaxed).
     */
    MOVED_EARLIER,

    /**
     * The task's schedule shift is driven by one or more dependency constraints from predecessors.
     */
    CONSTRAINT_DRIVEN
}
