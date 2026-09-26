package com.taskflow.taskflow.dependency.service;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.dependency.dto.DependencyGraphDto;
import com.taskflow.taskflow.dependency.dto.DependencyGraphEdgeDto;
import com.taskflow.taskflow.dependency.dto.DependencyGraphNodeDto;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only query service projecting project tasks and dependencies
 * into a visualization-safe graph DTO.
 *
 * Guarantees:
 * 1. Strict project isolation: only tasks and dependencies belonging to projectId are returned.
 * 2. Zero mutation: strictly read-only, does not alter task states, readiness, or schedules.
 * 3. Directional fidelity: edges preserve predecessorTaskId -> successorTaskId semantics.
 */
@Service
@Transactional(readOnly = true)
public class DependencyGraphQueryService {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphQueryService.class);

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;

    public DependencyGraphQueryService(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            TaskDependencyRepository dependencyRepository
    ) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
    }

    /**
     * Retrieves the visual dependency graph projection for the given project.
     *
     * @param projectId UUID of the project
     * @return project-scoped nodes and directed edges
     */
    public DependencyGraphDto getProjectDependencyGraph(UUID projectId) {
        Objects.requireNonNull(projectId, "Project ID must not be null");

        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findByProjectId(projectId);

        log.debug("Building dependency graph for project [{}] (tasks={}, dependencies={})",
                projectId, tasks.size(), dependencies.size());

        List<DependencyGraphNodeDto> nodes = tasks.stream()
                .sorted(Comparator.comparing(Task::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(t -> new DependencyGraphNodeDto(
                        t.getId(),
                        t.getTitle(),
                        t.getWorkflowStatus(),
                        t.getDependencyStatus(),
                        t.getScheduledStartDate(),
                        t.getScheduledDueDate(),
                        t.getPlannedStartDate(),
                        t.getDurationDays()
                ))
                .toList();

        List<DependencyGraphEdgeDto> edges = dependencies.stream()
                .map(d -> new DependencyGraphEdgeDto(
                        d.getId(),
                        d.getPredecessor().getId(),
                        d.getSuccessor().getId()
                ))
                .toList();

        return new DependencyGraphDto(projectId, nodes, edges);
    }
}
