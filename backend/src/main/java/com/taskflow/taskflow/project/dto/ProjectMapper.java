package com.taskflow.taskflow.project.dto;

import com.taskflow.taskflow.project.entity.Project;

public final class ProjectMapper {

    private ProjectMapper() {}

    public static Project toEntity(CreateProjectRequest request) {
        return new Project(request.name(), request.description());
    }

    public static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
