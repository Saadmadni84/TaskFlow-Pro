package com.taskflow.taskflow.dependency.readiness;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.common.metrics.TaskFlowMetrics;
import com.taskflow.taskflow.dependency.graph.AffectedSubgraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.dependency.graph.TopologicalSortService;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DependencyReadinessService {

    private static final Logger log = LoggerFactory.getLogger(DependencyReadinessService.class);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final DependencyGraphBuilder graphBuilder;
    private final GraphTraversalService traversalService;
    private final TopologicalSortService topologicalSortService;
    private final TaskFlowMetrics metrics;

    @Autowired
    public DependencyReadinessService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            DependencyGraphBuilder graphBuilder,
            GraphTraversalService traversalService,
            TopologicalSortService topologicalSortService,
            TaskFlowMetrics metrics
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.graphBuilder = graphBuilder;
        this.traversalService = traversalService;
        this.topologicalSortService = topologicalSortService;
        this.metrics = metrics != null ? metrics : new TaskFlowMetrics(null);
    }

    public DependencyReadinessService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            DependencyGraphBuilder graphBuilder,
            GraphTraversalService traversalService,
            TopologicalSortService topologicalSortService
    ) {
        this(taskRepository, projectRepository, graphBuilder, traversalService, topologicalSortService, new TaskFlowMetrics(null));
    }

    /**
     * Recalculates readiness for all downstream descendants affected by a change to rootTaskId.
     */
    @Transactional
    public List<Task> recalculateForTask(UUID rootTaskId) {
        return recalculateAffectedDescendants(rootTaskId);
    }

    /**
     * Recalculates readiness for all downstream descendants of rootTaskId.
     * Invoked when rootTaskId transitions between DONE and non-DONE workflow states.
     */
    @Transactional
    public List<Task> recalculateAffectedDescendants(UUID rootTaskId) {
        Task rootTask = taskRepository.findById(rootTaskId)
                .orElseThrow(() -> new TaskNotFoundException(rootTaskId));

        UUID projectId = rootTask.getProject().getId();
        DependencyGraph graph = graphBuilder.buildGraphForProject(projectId);

        AffectedSubgraph affectedSubgraph = traversalService.getAffectedSubgraph(graph, rootTaskId);
        if (!affectedSubgraph.hasAffectedTasks()) {
            return List.of();
        }

        return evaluateAndPersistReadiness(graph, projectId, affectedSubgraph.topologicalOrder());
    }

    /**
     * Recalculates readiness for targetTaskId and all its downstream descendants.
     * Invoked when a dependency edge is added or removed with targetTaskId as successor.
     */
    @Transactional
    public List<Task> recalculateTaskAndDescendants(UUID targetTaskId) {
        Task targetTask = taskRepository.findById(targetTaskId)
                .orElseThrow(() -> new TaskNotFoundException(targetTaskId));

        UUID projectId = targetTask.getProject().getId();
        DependencyGraph graph = graphBuilder.buildGraphForProject(projectId);

        Set<UUID> targetIds = new HashSet<>(traversalService.getDescendants(graph, targetTaskId));
        targetIds.add(targetTaskId);

        List<UUID> topologicalOrder = topologicalSortService.sortSubset(graph, targetIds);
        return evaluateAndPersistReadiness(graph, projectId, topologicalOrder);
    }

    /**
     * Recalculates readiness for all tasks in a project from scratch in full topological order.
     */
    @Transactional
    public List<Task> recalculateForProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }

        DependencyGraph graph = graphBuilder.buildGraphForProject(projectId);
        List<UUID> topologicalOrder = topologicalSortService.sort(graph);

        return evaluateAndPersistReadiness(graph, projectId, topologicalOrder);
    }

    /**
     * Evaluates predecessor states for tasks in topological order and persists only changed states.
     */
    private List<Task> evaluateAndPersistReadiness(
            DependencyGraph graph,
            UUID projectId,
            List<UUID> taskIdsInOrder
    ) {
        if (taskIdsInOrder == null || taskIdsInOrder.isEmpty()) {
            return List.of();
        }

        long startTime = System.currentTimeMillis();

        // Single batch fetch of all project tasks to avoid N+1 queries
        Map<UUID, Task> taskMap = taskRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        List<Task> changedTasks = new ArrayList<>();
        int readyCount = 0;
        int blockedCount = 0;

        for (UUID taskId : taskIdsInOrder) {
            Task task = taskMap.get(taskId);
            if (task == null) {
                continue;
            }

            Set<UUID> predecessorIds = graph.getPredecessors(taskId);
            List<ReadinessCalculator.PredecessorState> predStates = predecessorIds.stream()
                    .map(pid -> {
                        Task pred = taskMap.get(pid);
                        if (pred == null) {
                            return new ReadinessCalculator.PredecessorState(TaskStatus.BACKLOG, DependencyStatus.BLOCKED);
                        }
                        return new ReadinessCalculator.PredecessorState(pred.getWorkflowStatus(), pred.getDependencyStatus());
                    })
                    .toList();

            DependencyStatus newStatus = ReadinessCalculator.calculateFromStates(predStates);
            if (newStatus == DependencyStatus.READY) {
                readyCount++;
            } else {
                blockedCount++;
            }

            if (newStatus != task.getDependencyStatus()) {
                task.setDependencyStatus(newStatus);
                changedTasks.add(task);
            }
        }

        if (!changedTasks.isEmpty()) {
            taskRepository.saveAll(changedTasks);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("operation=DEPENDENCY_READINESS_RECALCULATION projectId={} affectedTaskCount={} changedTaskCount={} readyCount={} blockedCount={} durationMs={}",
                projectId, taskIdsInOrder.size(), changedTasks.size(), readyCount, blockedCount, durationMs);
        metrics.recordReadinessRecalculation(durationMs);

        return changedTasks;
    }
}
