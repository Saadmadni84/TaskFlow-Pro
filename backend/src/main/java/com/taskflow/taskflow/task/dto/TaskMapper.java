package com.taskflow.taskflow.task.dto;

import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;

import java.time.LocalDate;

public final class TaskMapper {

    private TaskMapper() {}

    public static Task toEntity(CreateTaskRequest request, Project project) {
        LocalDate start = request.resolvePlannedStart();
        return new Task(
                project,
                request.title(),
                request.description(),
                request.workflowStatus() != null ? request.workflowStatus() : TaskStatus.BACKLOG,
                start,
                request.dueDate(),
                request.durationDays()
        );
    }

    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getWorkflowStatus(),
                task.getDependencyStatus(),
                task.getPlannedStartDate(),
                task.getScheduledStartDate(),
                task.getScheduledDueDate(),
                task.getStartDate(),
                task.getDueDate(),
                task.getDurationDays(),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
