package com.taskflow.taskflow.scheduling.service;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.dependency.graph.AffectedSubgraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.dependency.graph.TopologicalSortService;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.scheduling.model.ScheduleCalculationResult;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.taskflow.taskflow.common.metrics.TaskFlowMetrics;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dependency-aware scheduling engine orchestrating schedule calculation and downstream propagation.
 *
 * Guarantees:
 * 1. Constraint-based calculation: successor.scheduledStartDate >= max(predecessor.scheduledDueDate + 1 day).
 * 2. Delay never compounds across converging dependency paths.
 * 3. Topological ordering ensures every predecessor is scheduled before its successors.
 * 4. Duration is strictly preserved.
 * 5. Recomputable baseline: tasks return to their planned start date when constraints are relaxed.
 * 6. Single-batch loading prevents N+1 database roundtrips.
 * 7. Transactional boundaries protect graph schedule consistency.
 */
@Service
@Transactional(readOnly = true)
public class SchedulingService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final DependencyGraphBuilder graphBuilder;
    private final GraphTraversalService traversalService;
    private final TopologicalSortService topologicalSortService;
    private final ScheduleCalculationService calculationService;
    private final TaskFlowMetrics metrics;

    @org.springframework.beans.factory.annotation.Autowired
    public SchedulingService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            DependencyGraphBuilder graphBuilder,
            GraphTraversalService traversalService,
            TopologicalSortService topologicalSortService,
            ScheduleCalculationService calculationService,
            TaskFlowMetrics metrics
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.graphBuilder = graphBuilder;
        this.traversalService = traversalService;
        this.topologicalSortService = topologicalSortService;
        this.calculationService = calculationService;
        this.metrics = metrics != null ? metrics : new TaskFlowMetrics(null);
    }

    public SchedulingService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            DependencyGraphBuilder graphBuilder,
            GraphTraversalService traversalService,
            TopologicalSortService topologicalSortService,
            ScheduleCalculationService calculationService
    ) {
        this(taskRepository, projectRepository, graphBuilder, traversalService,
                topologicalSortService, calculationService, new TaskFlowMetrics(null));
    }

    /**
     * Updates a task's user-defined planned schedule and propagates constraint shifts
     * to all downstream descendants in topological order.
     *
     * @param task the task entity to update
     * @param newPlannedStartDate new independent planned start date
     * @param newDurationDays duration in calendar days (must be >= 1)
     * @return the updated task entity
     */
    @Transactional
    public Task updateTaskSchedule(Task task, LocalDate newPlannedStartDate, Integer newDurationDays) {
        long startTime = System.currentTimeMillis();
        Objects.requireNonNull(task, "Task must not be null");

        UUID projectId = task.getProject().getId();
        DependencyGraph graph = graphBuilder.buildGraphForProject(projectId);

        LocalDate oldStart = task.getScheduledStartDate();

        // 1. Update task's planned schedule
        task.setPlannedSchedule(newPlannedStartDate, newDurationDays);

        // 2. Load all project tasks in a single batch to avoid N+1 queries
        Map<UUID, Task> taskMap = taskRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        taskMap.put(task.getId(), task);

        // 3. Recalculate root task itself against its current direct predecessors
        Set<UUID> predIds = graph.getPredecessors(task.getId());
        List<LocalDate> predDueDates = predIds.stream()
                .map(pid -> taskMap.get(pid) != null ? taskMap.get(pid).getScheduledDueDate() : null)
                .filter(Objects::nonNull)
                .toList();

        int duration = task.getDurationDays() != null && task.getDurationDays() > 0 ? task.getDurationDays() : 1;
        ScheduleCalculationResult selfResult = calculationService.calculateSchedule(
                task.getPlannedStartDate(),
                duration,
                predDueDates
        );
        task.setScheduledDates(selfResult.scheduledStartDate(), selfResult.scheduledDueDate());

        List<Task> tasksToSave = new ArrayList<>();
        tasksToSave.add(task);

        // 4. Find affected descendants and process in topological order
        AffectedSubgraph affected = traversalService.getAffectedSubgraph(graph, task.getId());
        int affectedCount = 1;
        if (affected.hasAffectedTasks()) {
            affectedCount += affected.getAffectedCount();
            List<Task> changedDescendants = evaluateSchedules(graph, affected.topologicalOrder(), taskMap);
            tasksToSave.addAll(changedDescendants);
        }

        taskRepository.saveAll(tasksToSave);

        long shiftDays = (oldStart != null && task.getScheduledStartDate() != null)
                ? java.time.temporal.ChronoUnit.DAYS.between(oldStart, task.getScheduledStartDate())
                : 0;

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("operation=SCHEDULE_PROPAGATION rootTaskId={} projectId={} affectedTaskCount={} changedTaskCount={} maxScheduleShiftDays={} durationMs={} result=SUCCESS",
                task.getId(), projectId, affectedCount, tasksToSave.size(), Math.abs(shiftDays), durationMs);
        metrics.recordSchedulePropagation(durationMs, true);

        return task;
    }

    /**
     * Recalculates schedules for all downstream descendants of rootTaskId.
     * Invoked when rootTaskId's scheduled dates change.
     */
    @Transactional
    public List<Task> recalculateAffectedDescendants(UUID rootTaskId) {
        Task rootTask = taskRepository.findById(rootTaskId)
                .orElseThrow(() -> new TaskNotFoundException(rootTaskId));

        UUID projectId = rootTask.getProject().getId();
        DependencyGraph graph = graphBuilder.buildGraphForProject(projectId);

        AffectedSubgraph affected = traversalService.getAffectedSubgraph(graph, rootTaskId);
        if (!affected.hasAffectedTasks()) {
            return List.of();
        }

        Map<UUID, Task> taskMap = taskRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        List<Task> changedTasks = evaluateSchedules(graph, affected.topologicalOrder(), taskMap);
        if (!changedTasks.isEmpty()) {
            taskRepository.saveAll(changedTasks);
        }
        return changedTasks;
    }

    /**
     * Recalculates schedule for targetTaskId and all its downstream descendants.
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

        Map<UUID, Task> taskMap = taskRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        List<Task> changedTasks = evaluateSchedules(graph, topologicalOrder, taskMap);
        if (!changedTasks.isEmpty()) {
            taskRepository.saveAll(changedTasks);
        }
        return changedTasks;
    }

    /**
     * Recalculates schedules for all tasks in a project in full topological order.
     */
    @Transactional
    public List<Task> recalculateForProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }

        DependencyGraph graph = graphBuilder.buildGraphForProject(projectId);
        List<UUID> topologicalOrder = topologicalSortService.sort(graph);

        Map<UUID, Task> taskMap = taskRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        List<Task> changedTasks = evaluateSchedules(graph, topologicalOrder, taskMap);
        if (!changedTasks.isEmpty()) {
            taskRepository.saveAll(changedTasks);
        }
        return changedTasks;
    }

    /**
     * Evaluates predecessor constraints for tasks in topological order.
     * Updates in-memory representations and tracks changed tasks.
     */
    private List<Task> evaluateSchedules(
            DependencyGraph graph,
            List<UUID> taskIdsInOrder,
            Map<UUID, Task> taskMap
    ) {
        if (taskIdsInOrder == null || taskIdsInOrder.isEmpty()) {
            return List.of();
        }

        List<Task> changedTasks = new ArrayList<>();

        for (UUID taskId : taskIdsInOrder) {
            Task task = taskMap.get(taskId);
            if (task == null || task.getPlannedStartDate() == null) {
                continue;
            }

            Set<UUID> predecessorIds = graph.getPredecessors(taskId);
            List<LocalDate> predecessorDueDates = predecessorIds.stream()
                    .map(pid -> taskMap.get(pid) != null ? taskMap.get(pid).getScheduledDueDate() : null)
                    .filter(Objects::nonNull)
                    .toList();

            int duration = task.getDurationDays() != null && task.getDurationDays() > 0
                    ? task.getDurationDays()
                    : 1;

            ScheduleCalculationResult result = calculationService.calculateSchedule(
                    task.getPlannedStartDate(),
                    duration,
                    predecessorDueDates
            );

            LocalDate oldStart = task.getScheduledStartDate();
            LocalDate oldDue = task.getScheduledDueDate();

            if (!Objects.equals(oldStart, result.scheduledStartDate()) || !Objects.equals(oldDue, result.scheduledDueDate())) {
                log.debug("Task [{}] schedule recalculated: start {} -> {}, due {} -> {}",
                        taskId, oldStart, result.scheduledStartDate(), oldDue, result.scheduledDueDate());
                task.setScheduledDates(result.scheduledStartDate(), result.scheduledDueDate());
                changedTasks.add(task);
            }
        }

        return changedTasks;
    }
}
