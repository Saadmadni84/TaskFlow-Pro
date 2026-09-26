package com.taskflow.taskflow.dependency.service;

import com.taskflow.taskflow.common.exception.CycleDetectedException;
import com.taskflow.taskflow.common.exception.DuplicateDependencyException;
import com.taskflow.taskflow.common.exception.InvalidDependencyException;
import com.taskflow.taskflow.common.exception.SelfDependencyException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.dto.DependencyMapper;
import com.taskflow.taskflow.dependency.dto.DependencyResponse;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.graph.AffectedSubgraph;
import com.taskflow.taskflow.dependency.graph.CycleDetectionService;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.dependency.readiness.DependencyReadinessService;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskDependencyService {

    private final TaskDependencyRepository dependencyRepository;
    private final TaskRepository taskRepository;
    private final DependencyGraphBuilder graphBuilder;
    private final CycleDetectionService cycleDetectionService;
    private final GraphTraversalService traversalService;
    private final DependencyReadinessService readinessService;

    public TaskDependencyService(
            TaskDependencyRepository dependencyRepository,
            TaskRepository taskRepository,
            DependencyGraphBuilder graphBuilder,
            CycleDetectionService cycleDetectionService,
            GraphTraversalService traversalService,
            DependencyReadinessService readinessService
    ) {
        this.dependencyRepository = dependencyRepository;
        this.taskRepository = taskRepository;
        this.graphBuilder = graphBuilder;
        this.cycleDetectionService = cycleDetectionService;
        this.traversalService = traversalService;
        this.readinessService = readinessService;
    }

    /**
     * Atomically validates and persists a new dependency edge:
     * predecessor -> successor
     *
     * Validates:
     * 1. Self-dependency rejection
     * 2. Both tasks exist
     * 3. Project isolation
     * 4. Duplicate edge rejection
     * 5. Cycle detection: does adding predecessor -> successor close a cycle?
     *
     * After persisting the edge, immediately recalculates readiness for the successor
     * and any of its downstream descendants.
     */
    @Transactional
    public DependencyResponse createDependency(CreateDependencyRequest request) {
        UUID predecessorId = request.predecessorTaskId();
        UUID successorId = request.successorTaskId();

        if (predecessorId == null || successorId == null) {
            throw new InvalidDependencyException("Predecessor and successor task IDs must not be null");
        }

        // Invariant 1: Self-dependency forbidden
        if (predecessorId.equals(successorId)) {
            throw new SelfDependencyException(predecessorId);
        }

        // Invariant 2: Both tasks must exist
        Task predecessor = taskRepository.findById(predecessorId)
                .orElseThrow(() -> new TaskNotFoundException(predecessorId));
        Task successor = taskRepository.findById(successorId)
                .orElseThrow(() -> new TaskNotFoundException(successorId));

        // Invariant 3: Project isolation - tasks must belong to the exact same project
        UUID predProjectId = predecessor.getProject().getId();
        UUID succProjectId = successor.getProject().getId();
        if (!predProjectId.equals(succProjectId)) {
            throw new InvalidDependencyException(
                    String.format("Cross-project dependencies are forbidden: predecessor project [%s] does not match successor project [%s]",
                            predProjectId, succProjectId)
            );
        }

        // Invariant 4: No duplicate dependency edges
        if (dependencyRepository.existsByPredecessorIdAndSuccessorId(predecessorId, successorId)) {
            throw new DuplicateDependencyException(
                    String.format("Dependency edge already exists from task [%s] to task [%s]", predecessorId, successorId)
            );
        }

        // Invariant 5: Cycle detection - targeted reachability check before persistence
        DependencyGraph projectGraph = graphBuilder.buildGraphForProject(predProjectId);
        if (cycleDetectionService.wouldCreateCycle(projectGraph, predecessorId, successorId)) {
            Optional<List<UUID>> cyclePath = cycleDetectionService.getPotentialCyclePath(projectGraph, predecessorId, successorId);
            throw new CycleDetectedException(
                    String.format("Circular dependency detected: adding dependency [%s -> %s] would create a cycle",
                            predecessorId, successorId),
                    cyclePath.orElse(List.of(predecessorId, successorId, predecessorId))
            );
        }

        TaskDependency dependency = new TaskDependency(predecessor, successor);
        TaskDependency saved = dependencyRepository.saveAndFlush(dependency);

        // Recalculate readiness for successor and any downstream descendants
        readinessService.recalculateTaskAndDescendants(successorId);

        return DependencyMapper.toResponse(saved);
    }

    public List<DependencyResponse> getDependenciesByPredecessor(UUID predecessorTaskId) {
        return dependencyRepository.findByPredecessorId(predecessorTaskId).stream()
                .map(DependencyMapper::toResponse)
                .toList();
    }

    public List<DependencyResponse> getDependenciesBySuccessor(UUID successorTaskId) {
        return dependencyRepository.findBySuccessorId(successorTaskId).stream()
                .map(DependencyMapper::toResponse)
                .toList();
    }

    public List<DependencyResponse> getDependenciesByProject(UUID projectId) {
        return dependencyRepository.findByProjectId(projectId).stream()
                .map(DependencyMapper::toResponse)
                .toList();
    }

    public DependencyGraph getProjectGraph(UUID projectId) {
        return graphBuilder.buildGraphForProject(projectId);
    }

    public AffectedSubgraph getAffectedSubgraph(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        DependencyGraph graph = graphBuilder.buildGraphForProject(task.getProject().getId());
        return traversalService.getAffectedSubgraph(graph, taskId);
    }

    /**
     * Removes an existing dependency edge and recalculates readiness for the successor
     * and any of its downstream descendants.
     */
    @Transactional
    public void deleteDependency(UUID predecessorTaskId, UUID successorTaskId) {
        if (!dependencyRepository.existsByPredecessorIdAndSuccessorId(predecessorTaskId, successorTaskId)) {
            throw new InvalidDependencyException("Dependency relationship does not exist");
        }
        dependencyRepository.deleteByPredecessorIdAndSuccessorId(predecessorTaskId, successorTaskId);
        dependencyRepository.flush();

        // Recalculate readiness for successor after prerequisite removal
        readinessService.recalculateTaskAndDescendants(successorTaskId);
    }
}
