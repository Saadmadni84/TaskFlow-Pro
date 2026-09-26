package com.taskflow.taskflow.ai.dto;

/**
 * Validated, grounded candidate dependency suggestion presented for human review.
 */
public record DependencySuggestionDto(
        TaskSummaryDto predecessor,
        TaskSummaryDto successor,
        double confidence,
        String reason
) {
}
