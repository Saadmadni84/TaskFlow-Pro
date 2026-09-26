package com.taskflow.taskflow.task.entity;

/**
 * Derived dependency status determined by prerequisite satisfaction in the DAG.
 *
 * READY: All prerequisite dependencies are satisfied (or task has no dependencies).
 * BLOCKED: One or more prerequisite dependencies are pending/incomplete.
 *
 * NOTE: This is derived state calculated by the dependency engine, NOT client-controlled.
 */
public enum DependencyStatus {
    READY,
    BLOCKED
}
