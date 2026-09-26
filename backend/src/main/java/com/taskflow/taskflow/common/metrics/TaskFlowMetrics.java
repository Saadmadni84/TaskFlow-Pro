package com.taskflow.taskflow.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized, lightweight application metrics using Micrometer.
 * Strictly enforces bounded, low-cardinality label dimensions (e.g., result, status).
 * Never tags metrics with unbound entity IDs (taskId, projectId, requestId).
 */
@Component
public class TaskFlowMetrics {

    private final MeterRegistry meterRegistry;

    public TaskFlowMetrics(@Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordTaskCreated() {
        if (meterRegistry == null) return;
        meterRegistry.counter("task_create_total").increment();
    }

    public void recordTaskUpdated(String statusChangeType) {
        if (meterRegistry == null) return;
        meterRegistry.counter("task_update_total", "change_type", statusChangeType != null ? statusChangeType : "none").increment();
    }

    public void recordTaskDeleted() {
        if (meterRegistry == null) return;
        meterRegistry.counter("task_delete_total").increment();
    }

    public void recordDependencyCreated(String result) {
        if (meterRegistry == null) return;
        meterRegistry.counter("dependency_create_total", "result", result != null ? result : "unknown").increment();
    }

    public void recordDependencyRemoved() {
        if (meterRegistry == null) return;
        meterRegistry.counter("dependency_remove_total").increment();
    }

    public void recordCycleRejection() {
        if (meterRegistry == null) return;
        meterRegistry.counter("dependency_cycle_rejection_total").increment();
    }

    public void recordSchedulePropagation(long durationMs, boolean success) {
        if (meterRegistry == null) return;
        meterRegistry.counter("schedule_propagation_total", "result", success ? "success" : "failure").increment();
        meterRegistry.timer("schedule_propagation_duration").record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordReadinessRecalculation(long durationMs) {
        if (meterRegistry == null) return;
        meterRegistry.counter("readiness_recalculation_total").increment();
        meterRegistry.timer("readiness_recalculation_duration").record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordAiSuggestion(long durationMs, String result) {
        if (meterRegistry == null) return;
        meterRegistry.counter("ai_suggestion_request_total", "result", result != null ? result : "unknown").increment();
        meterRegistry.timer("ai_suggestion_duration").record(durationMs, TimeUnit.MILLISECONDS);
        if ("error".equalsIgnoreCase(result) || "provider_error".equalsIgnoreCase(result) || "timeout".equalsIgnoreCase(result)) {
            meterRegistry.counter("ai_suggestion_failure_total").increment();
        }
    }

    public void recordAiFailure() {
        if (meterRegistry == null) return;
        meterRegistry.counter("ai_suggestion_failure_total").increment();
    }
}
