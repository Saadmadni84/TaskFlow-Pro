package com.taskflow.taskflow.dependency.graph;

import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Factory component responsible for loading project tasks and dependencies from persistence
 * and constructing a pure, project-isolated in-memory DependencyGraph.
 *
 * Runs single queries to avoid N+1 database roundtrips.
 */
@Component
public class DependencyGraphBuilder {

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;

    public DependencyGraphBuilder(TaskRepository taskRepository, TaskDependencyRepository dependencyRepository) {
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
    }

    /**
     * Builds an in-memory DependencyGraph containing all tasks and dependencies for a project.
     */
    @Transactional(readOnly = true)
    public DependencyGraph buildGraphForProject(UUID projectId) {
        Objects.requireNonNull(projectId, "Project ID must not be null");

        DependencyGraph graph = new DependencyGraph(projectId);

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        for (Task task : tasks) {
            graph.addNode(task.getId());
        }

        List<TaskDependency> dependencies = dependencyRepository.findByProjectId(projectId);
        for (TaskDependency dep : dependencies) {
            graph.addEdge(dep.getPredecessor().getId(), dep.getSuccessor().getId());
        }

        return graph;
    }
}
