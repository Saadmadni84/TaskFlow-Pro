package com.taskflow.taskflow.task.dto;

import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;

public final class TaskMapper {

    private TaskMapper() {}

    public static Task toEntity(CreateTaskRequest request, Project project) {
        return new Task(
                project,
                request.title(),
                request.description(),
                request.workflowStatus() != null ? request.workflowStatus() : TaskStatus.BACKLOG,
                request.startDate(),
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
                task.getStartDate(),
                task.getDueDate(),
                task.getDurationDays(),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
