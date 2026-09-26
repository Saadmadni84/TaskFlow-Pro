package com.taskflow.taskflow.dependency.dto;

import com.taskflow.taskflow.dependency.entity.TaskDependency;

public final class DependencyMapper {

    private DependencyMapper() {}

    public static DependencyResponse toResponse(TaskDependency dependency) {
        return new DependencyResponse(
                dependency.getId(),
                dependency.getPredecessor().getId(),
                dependency.getSuccessor().getId(),
                dependency.getCreatedAt()
        );
    }
}
