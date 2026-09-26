package com.taskflow.taskflow.dependency.readiness;

import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;

import java.util.Collection;

/**
 * Pure domain logic for evaluating task dependency readiness.
 *
 * Authoritative Rules:
 * 1. If a task has NO predecessors, it is READY.
 * 2. If a task has predecessors:
 *    Every predecessor must be satisfied.
 *    A predecessor is satisfied if its workflowStatus is DONE and it is not BLOCKED
 *    (its dependencyStatus is READY).
 * 3. If any predecessor is not satisfied, the task is BLOCKED.
 *
 * NOTE: Readiness status is NEVER derived from predecessor dependencyStatus alone.
 * It strictly requires predecessor workflowStatus == DONE AND predecessor readiness == READY.
 */
public final class ReadinessCalculator {

    private ReadinessCalculator() {}

    /**
     * Value object representing the status pair of a predecessor task.
     */
    public record PredecessorState(TaskStatus workflowStatus, DependencyStatus dependencyStatus) {
        public static PredecessorState of(TaskStatus workflowStatus, DependencyStatus dependencyStatus) {
            return new PredecessorState(workflowStatus, dependencyStatus);
        }

        public static PredecessorState done() {
            return new PredecessorState(TaskStatus.DONE, DependencyStatus.READY);
        }

        public static PredecessorState of(TaskStatus workflowStatus) {
            return new PredecessorState(workflowStatus, DependencyStatus.READY);
        }
    }

    /**
     * Calculates the derived DependencyStatus from a collection of predecessor workflow statuses,
     * assuming all completed predecessors are unblocked.
     *
     * @param predecessorWorkflowStatuses collection of predecessor workflow statuses
     * @return READY if empty or all are DONE; otherwise BLOCKED
     */
    public static DependencyStatus calculate(Collection<TaskStatus> predecessorWorkflowStatuses) {
        if (predecessorWorkflowStatuses == null || predecessorWorkflowStatuses.isEmpty()) {
            return DependencyStatus.READY;
        }

        for (TaskStatus status : predecessorWorkflowStatuses) {
            if (status != TaskStatus.DONE) {
                return DependencyStatus.BLOCKED;
            }
        }

        return DependencyStatus.READY;
    }

    /**
     * Calculates the derived DependencyStatus considering both workflowStatus and dependencyStatus
     * of each predecessor (authoritative multi-level evaluation).
     *
     * @param predecessorStates collection of predecessor states
     * @return READY if empty or all predecessors are DONE and READY; otherwise BLOCKED
     */
    public static DependencyStatus calculateFromStates(Collection<PredecessorState> predecessorStates) {
        if (predecessorStates == null || predecessorStates.isEmpty()) {
            return DependencyStatus.READY;
        }

        for (PredecessorState state : predecessorStates) {
            if (state == null) {
                return DependencyStatus.BLOCKED;
            }
            if (state.workflowStatus() != TaskStatus.DONE) {
                return DependencyStatus.BLOCKED;
            }
            if (state.dependencyStatus() != DependencyStatus.READY) {
                return DependencyStatus.BLOCKED;
            }
        }

        return DependencyStatus.READY;
    }

    /**
     * Calculates the derived DependencyStatus from actual Task predecessor entities.
     */
    public static DependencyStatus calculateFromTasks(Collection<Task> predecessors) {
        if (predecessors == null || predecessors.isEmpty()) {
            return DependencyStatus.READY;
        }

        for (Task pred : predecessors) {
            if (pred == null) {
                return DependencyStatus.BLOCKED;
            }
            if (pred.getWorkflowStatus() != TaskStatus.DONE) {
                return DependencyStatus.BLOCKED;
            }
            if (pred.getDependencyStatus() != DependencyStatus.READY) {
                return DependencyStatus.BLOCKED;
            }
        }

        return DependencyStatus.READY;
    }
}
