package com.taskflow.taskflow.scheduling.service;

import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.common.exception.ValidationException;
import com.taskflow.taskflow.dependency.graph.AffectedSubgraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.scheduling.dto.ScheduleImpactPreviewResponse;
import com.taskflow.taskflow.scheduling.dto.ScheduleImpactSummaryDto;
import com.taskflow.taskflow.scheduling.dto.SchedulePreviewRequest;
import com.taskflow.taskflow.scheduling.dto.TaskScheduleImpactDto;
import com.taskflow.taskflow.scheduling.model.ImpactType;
import com.taskflow.taskflow.scheduling.model.ReasonType;
import com.taskflow.taskflow.scheduling.model.ScheduleCalculationResult;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service providing deterministic, side-effect-free schedule impact previews.
 *
 * Reuses the authoritative Phase 5 {@link ScheduleCalculationService} to calculate simulated
 * schedules in-memory without mutating any database records or JPA entities.
 */
@Service
@Transactional(readOnly = true)
public class ScheduleImpactPreviewService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleImpactPreviewService.class);

    private final TaskRepository taskRepository;
    private final DependencyGraphBuilder graphBuilder;
    private final GraphTraversalService traversalService;
    private final ScheduleCalculationService calculationService;

    public ScheduleImpactPreviewService(
            TaskRepository taskRepository,
            DependencyGraphBuilder graphBuilder,
            GraphTraversalService traversalService,
            ScheduleCalculationService calculationService
    ) {
        this.taskRepository = taskRepository;
        this.graphBuilder = graphBuilder;
        this.traversalService = traversalService;
        this.calculationService = calculationService;
    }

    /**
     * Calculates the downstream schedule impact of proposing a new planned start date
     * for the specified task.
     *
     * @param taskId the ID of the source task to evaluate
     * @param request the proposed schedule change
     * @return side-effect-free preview containing current vs proposed schedules and impact explanations
     */
    public ScheduleImpactPreviewResponse calculatePreview(UUID taskId, SchedulePreviewRequest request) {
        Objects.requireNonNull(taskId, "Task ID must not be null");
        if (request == null || request.plannedStartDate() == null) {
            throw new ValidationException("Proposed planned start date must not be null");
        }

        log.info("Schedule impact preview requested for taskId={} with proposed plannedStart={}",
                taskId, request.plannedStartDate());

        Task sourceTask = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        UUID projectId = sourceTask.getProject().getId();
        DependencyGraph graph = graphBuilder.buildGraphForProject(projectId);
        AffectedSubgraph affected = traversalService.getAffectedSubgraph(graph, taskId);

        // Load all tasks for the project in a single batch to avoid N+1 queries
        Map<UUID, Task> projectTasks = taskRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        // In-memory simulation registry: taskId -> SimulatedSchedule
        Map<UUID, SimulatedSchedule> simulationMap = new HashMap<>();

        // 1. Simulate the source task itself
        SimulatedSchedule sourceSim = simulateSourceTask(sourceTask, request.plannedStartDate(), graph, projectTasks);
        simulationMap.put(sourceTask.getId(), sourceSim);

        // 2. Simulate affected downstream descendants in topological order
        List<SimulatedSchedule> descendantSims = new ArrayList<>();
        for (UUID descendantId : affected.topologicalOrder()) {
            Task descendant = projectTasks.get(descendantId);
            if (descendant == null) {
                continue;
            }
            SimulatedSchedule descSim = simulateDescendantTask(descendant, graph, projectTasks, simulationMap);
            simulationMap.put(descendantId, descSim);
            descendantSims.add(descSim);
        }

        // 3. Assemble ordered list of task impact DTOs (source task first, followed by descendants in topological order)
        List<TaskScheduleImpactDto> impactList = new ArrayList<>();
        impactList.add(sourceSim.toDto());
        for (SimulatedSchedule descSim : descendantSims) {
            impactList.add(descSim.toDto());
        }

        // 4. Calculate summary metrics
        int affectedCount = impactList.size();
        int changedCount = (int) impactList.stream()
                .filter(t -> t.startShiftDays() != 0 || t.dueShiftDays() != 0)
                .count();
        int unchangedCount = affectedCount - changedCount;
        int maxDelayDays = impactList.stream()
                .mapToInt(TaskScheduleImpactDto::startShiftDays)
                .filter(shift -> shift > 0)
                .max()
                .orElse(0);

        ScheduleImpactSummaryDto summary = new ScheduleImpactSummaryDto(
                affectedCount,
                changedCount,
                unchangedCount,
                maxDelayDays
        );

        log.info("Schedule impact preview completed for taskId={}, affectedTaskCount={}, changedTaskCount={}, maxDelayDays={}",
                taskId, affectedCount, changedCount, maxDelayDays);

        return new ScheduleImpactPreviewResponse(taskId, summary, impactList);
    }

    private SimulatedSchedule simulateSourceTask(
            Task sourceTask,
            LocalDate proposedPlannedStart,
            DependencyGraph graph,
            Map<UUID, Task> projectTasks
    ) {
        int duration = getTaskDuration(sourceTask);

        // Gather predecessor due dates for source task (predecessors of root are not affected descendants)
        Set<UUID> predecessorIds = graph.getPredecessors(sourceTask.getId());
        List<LocalDate> predDueDates = new ArrayList<>();
        Map<UUID, LocalDate> predDueMap = new HashMap<>();

        for (UUID pid : predecessorIds) {
            Task predTask = projectTasks.get(pid);
            if (predTask != null && predTask.getScheduledDueDate() != null) {
                predDueDates.add(predTask.getScheduledDueDate());
                predDueMap.put(pid, predTask.getScheduledDueDate());
            }
        }

        ScheduleCalculationResult result = calculationService.calculateSchedule(
                proposedPlannedStart,
                duration,
                predDueDates
        );

        LocalDate currentPlanned = sourceTask.getPlannedStartDate();
        LocalDate currentStart = sourceTask.getScheduledStartDate();
        LocalDate currentDue = sourceTask.getScheduledDueDate();

        LocalDate proposedStart = result.scheduledStartDate();
        LocalDate proposedDue = result.scheduledDueDate();

        int startShift = calculateShift(currentStart, proposedStart);
        int dueShift = calculateShift(currentDue, proposedDue);

        // Binding predecessors
        List<UUID> bindingPredIds = findBindingPredecessors(result.latestPredecessorConstraint(), predDueMap);

        ImpactType impactType;
        if (startShift == 0 && dueShift == 0) {
            impactType = ImpactType.UNCHANGED;
        } else if (startShift > 0) {
            impactType = ImpactType.DELAYED;
        } else {
            impactType = ImpactType.MOVED_EARLIER;
        }

        String reason;
        if (startShift == 0 && dueShift == 0) {
            reason = "Proposed planned start date matches current planned start date; no schedule change.";
        } else if (startShift > 0) {
            reason = String.format("Source task planned start moved later by %d %s.",
                    startShift, startShift == 1 ? "day" : "days");
        } else {
            reason = String.format("Source task planned start moved earlier by %d %s.",
                    Math.abs(startShift), Math.abs(startShift) == 1 ? "day" : "days");
        }

        return new SimulatedSchedule(
                sourceTask.getId(),
                sourceTask.getTitle(),
                currentPlanned,
                proposedPlannedStart,
                currentStart,
                proposedStart,
                currentDue,
                proposedDue,
                duration,
                startShift,
                dueShift,
                impactType,
                ReasonType.SOURCE_TASK_CHANGE,
                bindingPredIds,
                result.latestPredecessorConstraint(),
                reason
        );
    }

    private SimulatedSchedule simulateDescendantTask(
            Task descendant,
            DependencyGraph graph,
            Map<UUID, Task> projectTasks,
            Map<UUID, SimulatedSchedule> simulationMap
    ) {
        int duration = getTaskDuration(descendant);
        LocalDate proposedPlannedStart = descendant.getPlannedStartDate();

        Set<UUID> predecessorIds = graph.getPredecessors(descendant.getId());
        List<LocalDate> predDueDates = new ArrayList<>();
        Map<UUID, LocalDate> predDueMap = new HashMap<>();
        Map<UUID, String> predTitleMap = new HashMap<>();

        for (UUID pid : predecessorIds) {
            LocalDate due = null;
            String title = null;
            if (simulationMap.containsKey(pid)) {
                due = simulationMap.get(pid).proposedScheduledDue;
                title = simulationMap.get(pid).title;
            } else if (projectTasks.containsKey(pid)) {
                due = projectTasks.get(pid).getScheduledDueDate();
                title = projectTasks.get(pid).getTitle();
            }

            if (due != null) {
                predDueDates.add(due);
                predDueMap.put(pid, due);
                predTitleMap.put(pid, title != null ? title : pid.toString());
            }
        }

        ScheduleCalculationResult result = calculationService.calculateSchedule(
                proposedPlannedStart,
                duration,
                predDueDates
        );

        LocalDate currentPlanned = descendant.getPlannedStartDate();
        LocalDate currentStart = descendant.getScheduledStartDate();
        LocalDate currentDue = descendant.getScheduledDueDate();

        LocalDate proposedStart = result.scheduledStartDate();
        LocalDate proposedDue = result.scheduledDueDate();

        int startShift = calculateShift(currentStart, proposedStart);
        int dueShift = calculateShift(currentDue, proposedDue);

        LocalDate latestConstraint = result.latestPredecessorConstraint();
        List<UUID> bindingPredIds = findBindingPredecessors(latestConstraint, predDueMap);

        List<String> bindingPredTitles = bindingPredIds.stream()
                .map(id -> predTitleMap.getOrDefault(id, id.toString()))
                .toList();

        // Categorize impact and construct explanatory reason
        ImpactType impactType;
        ReasonType reasonType;
        String reason;

        if (startShift == 0 && dueShift == 0) {
            impactType = ImpactType.UNCHANGED;
            if (proposedPlannedStart != null && latestConstraint != null
                    && !proposedPlannedStart.isBefore(latestConstraint)) {
                reasonType = ReasonType.PLANNED_DATE_DOMINANT;
                reason = String.format("%s's planned schedule (%s) is already later than the dependency constraint (%s); schedule is unchanged.",
                        descendant.getTitle(), proposedPlannedStart, latestConstraint);
            } else if (latestConstraint != null) {
                reasonType = ReasonType.DEPENDENCY_CONSTRAINT;
                reason = String.format("%s remains on schedule at %s; predecessor constraint (%s) does not delay it.",
                        descendant.getTitle(), proposedStart, latestConstraint);
            } else {
                reasonType = ReasonType.NO_CHANGE;
                reason = "Task schedule is unchanged.";
            }
        } else if (startShift > 0) {
            // Task is delayed
            if (latestConstraint != null && Objects.equals(proposedStart, latestConstraint)) {
                impactType = ImpactType.CONSTRAINT_DRIVEN;
                reasonType = ReasonType.DEPENDENCY_CONSTRAINT;
                if (bindingPredIds.size() > 1) {
                    // Converging paths: multiple predecessors finish on the same date and constrain this successor
                    reason = String.format("%s is constrained by multiple predecessors (%s) finishing on the same date. Non-compounding scheduling applies; earliest allowed start is %s.",
                            descendant.getTitle(), String.join(", ", bindingPredTitles), latestConstraint);
                } else if (bindingPredIds.size() == 1) {
                    UUID bindingId = bindingPredIds.get(0);
                    LocalDate bindingDue = predDueMap.get(bindingId);
                    String bindingTitle = bindingPredTitles.get(0);
                    reason = String.format("%s must start on or after %s, constrained by predecessor [%s] due on %s.",
                            descendant.getTitle(), latestConstraint, bindingTitle, bindingDue);
                } else {
                    reason = String.format("%s is constrained by dependency requirements; earliest allowed start is %s.",
                            descendant.getTitle(), latestConstraint);
                }
            } else {
                impactType = ImpactType.DELAYED;
                reasonType = ReasonType.PLANNED_DATE_DOMINANT;
                reason = String.format("%s scheduled start is driven by planned start date (%s).",
                        descendant.getTitle(), proposedPlannedStart);
            }
        } else {
            // Task moved earlier
            impactType = ImpactType.MOVED_EARLIER;
            if (latestConstraint != null && Objects.equals(proposedStart, latestConstraint)) {
                reasonType = ReasonType.DEPENDENCY_CONSTRAINT;
                reason = String.format("%s moved earlier to %s as predecessor constraint relaxed.",
                        descendant.getTitle(), proposedStart);
            } else if (proposedPlannedStart != null && Objects.equals(proposedStart, proposedPlannedStart)) {
                reasonType = ReasonType.PLANNED_DATE_DOMINANT;
                reason = String.format("%s returned toward planned baseline start (%s) as predecessor constraint relaxed.",
                        descendant.getTitle(), proposedPlannedStart);
            } else {
                reasonType = ReasonType.DEPENDENCY_CONSTRAINT;
                reason = String.format("%s moved earlier to %s.", descendant.getTitle(), proposedStart);
            }
        }

        return new SimulatedSchedule(
                descendant.getId(),
                descendant.getTitle(),
                currentPlanned,
                proposedPlannedStart,
                currentStart,
                proposedStart,
                currentDue,
                proposedDue,
                duration,
                startShift,
                dueShift,
                impactType,
                reasonType,
                bindingPredIds,
                latestConstraint,
                reason
        );
    }

    private List<UUID> findBindingPredecessors(LocalDate latestConstraint, Map<UUID, LocalDate> predDueMap) {
        if (latestConstraint == null || predDueMap.isEmpty()) {
            return List.of();
        }
        return predDueMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().plusDays(1).isEqual(latestConstraint))
                .map(Map.Entry::getKey)
                .toList();
    }

    private int calculateShift(LocalDate current, LocalDate proposed) {
        if (current == null || proposed == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(current, proposed);
    }

    private int getTaskDuration(Task task) {
        return task.getDurationDays() != null && task.getDurationDays() > 0 ? task.getDurationDays() : 1;
    }

    /**
     * Pure in-memory representation of a task's simulated schedule during preview calculation.
     */
    private record SimulatedSchedule(
            UUID taskId,
            String title,
            LocalDate currentPlannedStart,
            LocalDate proposedPlannedStart,
            LocalDate currentScheduledStart,
            LocalDate proposedScheduledStart,
            LocalDate currentScheduledDue,
            LocalDate proposedScheduledDue,
            int durationDays,
            int startShiftDays,
            int dueShiftDays,
            ImpactType impactType,
            ReasonType reasonType,
            List<UUID> constraintSourceTaskIds,
            LocalDate constraintDate,
            String reason
    ) {
        public TaskScheduleImpactDto toDto() {
            return new TaskScheduleImpactDto(
                    taskId,
                    title,
                    currentPlannedStart,
                    proposedPlannedStart,
                    currentScheduledStart,
                    proposedScheduledStart,
                    currentScheduledDue,
                    proposedScheduledDue,
                    durationDays,
                    startShiftDays,
                    dueShiftDays,
                    startShiftDays, // shiftDays convenience alias
                    impactType,
                    reasonType,
                    constraintSourceTaskIds,
                    constraintDate,
                    reason
            );
        }
    }
}
