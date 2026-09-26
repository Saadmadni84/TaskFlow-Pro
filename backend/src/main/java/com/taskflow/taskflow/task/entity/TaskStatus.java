package com.taskflow.taskflow.task.entity;

/**
 * Workflow status representing the user-facing Kanban column placement.
 * Exactly four valid states: BACKLOG, IN_PROGRESS, REVIEW, DONE.
 *
 * NOTE: READY and BLOCKED are dependency states, NOT workflow statuses.
 */
public enum TaskStatus {
    BACKLOG,
    IN_PROGRESS,
    REVIEW,
    DONE
}
