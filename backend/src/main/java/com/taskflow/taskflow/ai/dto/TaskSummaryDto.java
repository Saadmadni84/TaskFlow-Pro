package com.taskflow.taskflow.ai.dto;

import java.util.UUID;

/**
 * Summary descriptor of a task included in a dependency suggestion to avoid redundant client queries.
 */
public record TaskSummaryDto(
        UUID taskId,
        String title
) {
}
