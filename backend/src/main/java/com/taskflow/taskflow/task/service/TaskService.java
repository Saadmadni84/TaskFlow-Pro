package com.taskflow.taskflow.task.service;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.common.metrics.TaskFlowMetrics;
import com.taskflow.taskflow.dependency.readiness.DependencyReadinessService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.scheduling.service.SchedulingService;
import com.taskflow.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.taskflow.task.dto.TaskMapper;
import com.taskflow.taskflow.task.dto.TaskResponse;
import com.taskflow.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final DependencyReadinessService readinessService;
    private final SchedulingService schedulingService;
    private final TaskFlowMetrics metrics;

    @Autowired
    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            DependencyReadinessService readinessService,
            SchedulingService schedulingService,
            TaskFlowMetrics metrics
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.readinessService = readinessService;
        this.schedulingService = schedulingService;
        this.metrics = metrics != null ? metrics : new TaskFlowMetrics(null);
    }

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            DependencyReadinessService readinessService,
            SchedulingService schedulingService
    ) {
        this(taskRepository, projectRepository, readinessService, schedulingService, new TaskFlowMetrics(null));
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        long startTime = System.currentTimeMillis();
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ProjectNotFoundException(request.projectId()));

        Task task = TaskMapper.toEntity(request, project);
        Task saved = taskRepository.save(task);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("operation=TASK_CREATE taskId={} projectId={} workflowStatus={} durationMs={}",
                saved.getId(), project.getId(), saved.getWorkflowStatus(), durationMs);
        metrics.recordTaskCreated();

        return TaskMapper.toResponse(saved);
    }

    public Task getTaskEntity(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    public TaskResponse getTask(UUID id) {
        return TaskMapper.toResponse(getTaskEntity(id));
    }

    public List<TaskResponse> getTasksByProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return taskRepository.findByProjectId(projectId).stream()
                .map(TaskMapper::toResponse)
                .toList();
    }

    /**
     * Updates an existing task.
     * 1. When workflowStatus changes between DONE and non-DONE, automatically propagates
     *    readiness state recalculations to all affected downstream descendants.
     * 2. When planned schedule dates or duration change, automatically propagates
     *    constraint-based schedule shifts to all affected downstream descendants.
     */
    @Transactional
    public TaskResponse updateTask(UUID id, UpdateTaskRequest request) {
        long startTime = System.currentTimeMillis();
        Task task = getTaskEntity(id);
        task.setTitle(request.title());
        task.setDescription(request.description());

        TaskStatus oldStatus = task.getWorkflowStatus();
        TaskStatus newStatus = request.workflowStatus();
        boolean statusChanged = (newStatus != null && newStatus != oldStatus);

        if (statusChanged) {
            task.setWorkflowStatus(newStatus);
            boolean wasDone = (oldStatus == TaskStatus.DONE);
            boolean isDone = (newStatus == TaskStatus.DONE);
            // Trigger downstream recalculation only when completion state toggles
            if (wasDone != isDone) {
                readinessService.recalculateAffectedDescendants(task.getId());
            }
        }

        LocalDate newPlannedStart = request.resolvePlannedStart();
        Integer newDuration = request.durationDays();

        LocalDate oldPlanned = task.getPlannedStartDate();
        Integer oldDuration = task.getDurationDays();

        boolean scheduleChanged = (newPlannedStart != null && !Objects.equals(newPlannedStart, oldPlanned))
                || (newDuration != null && !Objects.equals(newDuration, oldDuration));

        if (scheduleChanged) {
            schedulingService.updateTaskSchedule(task, newPlannedStart, newDuration);
        } else if (newPlannedStart == null && request.startDate() == null && request.dueDate() != null) {
            // legacy dueDate adjustment
            task.setDatesAndDuration(request.startDate(), request.dueDate(), request.durationDays());
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("operation=TASK_UPDATE taskId={} projectId={} oldStatus={} newStatus={} scheduleChanged={} durationMs={}",
                id, task.getProject().getId(), oldStatus, newStatus, scheduleChanged, durationMs);
        metrics.recordTaskUpdated(statusChanged ? (newStatus == TaskStatus.DONE ? "completed" : "status_changed") : "none");

        return TaskMapper.toResponse(task);
    }

    @Transactional
    public void deleteTask(UUID id) {
        long startTime = System.currentTimeMillis();
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("operation=TASK_DELETE taskId={} durationMs={}", id, durationMs);
        metrics.recordTaskDeleted();
    }
}
