package com.taskflow.taskflow.criticalpath.service;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.criticalpath.dto.CriticalPathResponse;
import com.taskflow.taskflow.criticalpath.dto.TaskMetricsDto;
import com.taskflow.taskflow.criticalpath.graph.CriticalPathCalculator;
import com.taskflow.taskflow.criticalpath.model.CriticalPathResult;
import com.taskflow.taskflow.criticalpath.model.TaskScheduleInput;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service orchestrating project-scoped Critical Path Analysis.
 *
 * Guarantees:
 * 1. Read-only and side-effect free: performs no database mutations or schedule updates.
 * 2. Single-batch loading prevents N+1 queries.
 * 3. Uses current dependency-adjusted scheduled dates and preserved durations.
 * 4. Delegates pure CPM mathematical analysis to CriticalPathCalculator.
 */
@Service
@Transactional(readOnly = true)
public class CriticalPathService {

    private static final Logger log = LoggerFactory.getLogger(CriticalPathService.class);

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final CriticalPathCalculator calculator;

    public CriticalPathService(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            TaskDependencyRepository dependencyRepository,
            CriticalPathCalculator calculator
    ) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.calculator = calculator;
    }

    /**
     * Computes the deterministic Critical Path Analysis for the specified project.
     *
     * @param projectId UUID of the target project
     * @return structured CriticalPathResponse containing completion date, critical tasks, and paths
     */
    public CriticalPathResponse calculateCriticalPath(UUID projectId) {
        Objects.requireNonNull(projectId, "Project ID must not be null");

        // 1. Validate project existence
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }

        // 2. Load all project tasks in a single batch
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        if (tasks.isEmpty()) {
            log.info("Critical path requested for empty project [{}]", projectId);
            return new CriticalPathResponse(projectId, null, null, List.of(), List.of(), List.of());
        }

        // 3. Load all dependencies for the project
        List<TaskDependency> dependencies = dependencyRepository.findByProjectId(projectId);

        // 4. Construct calculation inputs and dependency graph
        List<TaskScheduleInput> inputs = new ArrayList<>(tasks.size());
        DependencyGraph graph = new DependencyGraph(projectId);

        for (Task task : tasks) {
            int duration = task.getDurationDays() != null && task.getDurationDays() > 0 ? task.getDurationDays() : 1;
            inputs.add(new TaskScheduleInput(
                    task.getId(),
                    task.getTitle(),
                    task.getScheduledStartDate(),
                    task.getScheduledDueDate(),
                    task.getPlannedStartDate(),
                    duration
            ));
            graph.addNode(task.getId());
        }

        for (TaskDependency dep : dependencies) {
            graph.addEdge(dep.getPredecessor().getId(), dep.getSuccessor().getId());
        }

        log.info("Calculating critical path for project [{}] (tasks={}, dependencies={})",
                projectId, tasks.size(), dependencies.size());

        long startTime = System.currentTimeMillis();
        // 5. Execute pure CPM calculation
        CriticalPathResult result = calculator.calculate(inputs, graph);

        // 6. Map domain metrics to response DTOs
        List<TaskMetricsDto> taskMetrics = result.taskMetrics().stream()
                .map(m -> new TaskMetricsDto(
                        m.taskId(),
                        m.title(),
                        m.durationDays(),
                        m.earliestStart(),
                        m.earliestFinish(),
                        m.latestStart(),
                        m.latestFinish(),
                        m.totalSlackDays(),
                        m.isCritical()
                ))
                .toList();

        long projectDurationDays = (result.projectStartDate() != null && result.projectCompletionDate() != null)
                ? java.time.temporal.ChronoUnit.DAYS.between(result.projectStartDate(), result.projectCompletionDate()) + 1
                : 0;

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("operation=CRITICAL_PATH_CALCULATION projectId={} taskCount={} criticalTaskCount={} criticalPathCount={} projectDurationDays={} durationMs={}",
                projectId, tasks.size(), result.criticalTaskIds().size(), result.criticalPaths().size(), projectDurationDays, durationMs);

        return new CriticalPathResponse(
                projectId,
                result.projectStartDate(),
                result.projectCompletionDate(),
                result.criticalTaskIds(),
                result.criticalPaths(),
                taskMetrics
        );
    }
}
