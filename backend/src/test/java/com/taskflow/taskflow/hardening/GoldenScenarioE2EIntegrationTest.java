package com.taskflow.taskflow.hardening;

import com.taskflow.taskflow.ai.dto.AcceptSuggestionRequest;
import com.taskflow.taskflow.ai.service.DependencySuggestionService;
import com.taskflow.taskflow.criticalpath.dto.CriticalPathResponse;
import com.taskflow.taskflow.criticalpath.dto.TaskMetricsDto;
import com.taskflow.taskflow.criticalpath.service.CriticalPathService;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.scheduling.service.SchedulingService;
import com.taskflow.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import com.taskflow.taskflow.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GoldenScenarioE2EIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private TaskDependencyService dependencyService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private SchedulingService schedulingService;

    @Autowired
    private CriticalPathService criticalPathService;

    @Autowired
    private DependencySuggestionService suggestionService;

    private Project project;
    private Task taskA;
    private Task taskB;
    private Task taskC;
    private Task taskD;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(
                new Project("Golden Scenario E2E Project", "Comprehensive end-to-end regression validation")
        );

        // Build diamond converging graph:
        // Task A: duration 5, 2026-06-01 -> 2026-06-05
        // Task B: duration 3
        // Task C: duration 4
        // Task D: duration 2
        taskA = taskRepository.saveAndFlush(new Task(project, "Task A - Requirements & Architecture", "Architecture definition",
                TaskStatus.BACKLOG, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), 5));
        taskB = taskRepository.saveAndFlush(new Task(project, "Task B - Frontend Scaffolding", "UI setup",
                TaskStatus.BACKLOG, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 3));
        taskC = taskRepository.saveAndFlush(new Task(project, "Task C - Backend API Service", "API endpoints",
                TaskStatus.BACKLOG, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 4), 4));
        taskD = taskRepository.saveAndFlush(new Task(project, "Task D - Integration & Verification", "Full stack verification",
                TaskStatus.BACKLOG, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 2));

        // Connect A -> B, A -> C, B -> D, C -> D
        dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskB.getId()));
        dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskC.getId()));
        dependencyService.createDependency(new CreateDependencyRequest(taskB.getId(), taskD.getId()));
        dependencyService.createDependency(new CreateDependencyRequest(taskC.getId(), taskD.getId()));
    }

    @Test
    @DisplayName("Golden Scenario: DAG readiness, no-compounding scheduling, baseline recovery, CPM slack, and human-accepted AI suggestion")
    void executeGoldenScenarioEndToEnd() {
        // =========================================================================
        // STEP 1: Verify Initial Scheduled Dates and Readiness
        // =========================================================================
        Task refA = taskRepository.findById(taskA.getId()).orElseThrow();
        Task refB = taskRepository.findById(taskB.getId()).orElseThrow();
        Task refC = taskRepository.findById(taskC.getId()).orElseThrow();
        Task refD = taskRepository.findById(taskD.getId()).orElseThrow();

        // Task A has no prerequisites: READY
        assertThat(refA.getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(refA.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(refA.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 5));

        // Task B and C depend on A: BLOCKED, scheduled after A ends
        assertThat(refB.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        assertThat(refB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(refB.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 8));

        assertThat(refC.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        assertThat(refC.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(refC.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 9));

        // Task D depends on B (ends 06-08) and C (ends 06-09): BLOCKED, scheduled after max(B, C) -> 2026-06-10
        assertThat(refD.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        assertThat(refD.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(refD.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 11));

        // =========================================================================
        // STEP 2: Critical Path Analysis (Slack and Critical Sequence)
        // =========================================================================
        CriticalPathResponse cpm = criticalPathService.calculateCriticalPath(project.getId());
        assertThat(cpm.criticalTaskIds()).containsExactlyInAnyOrder(taskA.getId(), taskC.getId(), taskD.getId());
        assertThat(cpm.projectStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(cpm.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        long durationDays = ChronoUnit.DAYS.between(cpm.projectStartDate(), cpm.projectCompletionDate()) + 1;
        assertThat(durationDays).isEqualTo(11);

        Map<UUID, TaskMetricsDto> metricsMap = cpm.tasks().stream()
                .collect(Collectors.toMap(TaskMetricsDto::taskId, m -> m));

        // A, C, D have zero slack
        assertThat(metricsMap.get(taskA.getId()).totalSlackDays()).isEqualTo(0);
        assertThat(metricsMap.get(taskA.getId()).isCritical()).isTrue();

        assertThat(metricsMap.get(taskC.getId()).totalSlackDays()).isEqualTo(0);
        assertThat(metricsMap.get(taskC.getId()).isCritical()).isTrue();

        assertThat(metricsMap.get(taskD.getId()).totalSlackDays()).isEqualTo(0);
        assertThat(metricsMap.get(taskD.getId()).isCritical()).isTrue();

        // B has 1 day of slack (finishes 06-08, but D only begins 06-10)
        assertThat(metricsMap.get(taskB.getId()).totalSlackDays()).isEqualTo(1);
        assertThat(metricsMap.get(taskB.getId()).isCritical()).isFalse();

        // =========================================================================
        // STEP 3: Downstream Readiness Cascading
        // =========================================================================
        // Mark A as DONE -> B and C should become READY, D remains BLOCKED
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.DONE,
                taskA.getStartDate(), taskA.getDueDate(), taskA.getDurationDays()
        ));

        assertThat(taskRepository.findById(taskB.getId()).orElseThrow().getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(taskRepository.findById(taskC.getId()).orElseThrow().getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(taskRepository.findById(taskD.getId()).orElseThrow().getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Mark B as DONE -> D must remain BLOCKED because C is still in BACKLOG
        taskService.updateTask(taskB.getId(), new UpdateTaskRequest(
                taskB.getTitle(), taskB.getDescription(), TaskStatus.DONE,
                taskB.getStartDate(), taskB.getDueDate(), taskB.getDurationDays()
        ));
        assertThat(taskRepository.findById(taskD.getId()).orElseThrow().getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Mark C as DONE -> D now has all prerequisites DONE, transitions to READY
        taskService.updateTask(taskC.getId(), new UpdateTaskRequest(
                taskC.getTitle(), taskC.getDescription(), TaskStatus.DONE,
                taskC.getStartDate(), taskC.getDueDate(), taskC.getDurationDays()
        ));
        assertThat(taskRepository.findById(taskD.getId()).orElseThrow().getDependencyStatus()).isEqualTo(DependencyStatus.READY);

        // Restore tasks to BACKLOG for scheduling verification
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(taskA.getTitle(), taskA.getDescription(), TaskStatus.BACKLOG, taskA.getStartDate(), taskA.getDueDate(), taskA.getDurationDays()));
        taskService.updateTask(taskB.getId(), new UpdateTaskRequest(taskB.getTitle(), taskB.getDescription(), TaskStatus.BACKLOG, taskB.getStartDate(), taskB.getDueDate(), taskB.getDurationDays()));
        taskService.updateTask(taskC.getId(), new UpdateTaskRequest(taskC.getTitle(), taskC.getDescription(), TaskStatus.BACKLOG, taskC.getStartDate(), taskC.getDueDate(), taskC.getDurationDays()));

        // =========================================================================
        // STEP 4: Upstream Delay & No-Compounding Verification (+3 days)
        // =========================================================================
        LocalDate delayedStart = LocalDate.of(2026, 6, 4); // +3 days from 2026-06-01
        Task currentA = taskRepository.findById(taskA.getId()).orElseThrow();
        schedulingService.updateTaskSchedule(currentA, delayedStart, currentA.getDurationDays());

        Task delayedA = taskRepository.findById(taskA.getId()).orElseThrow();
        Task delayedB = taskRepository.findById(taskB.getId()).orElseThrow();
        Task delayedC = taskRepository.findById(taskC.getId()).orElseThrow();
        Task delayedD = taskRepository.findById(taskD.getId()).orElseThrow();

        assertThat(delayedA.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(delayedA.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 8));

        assertThat(delayedB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(delayedB.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 11));

        assertThat(delayedC.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(delayedC.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 12));

        // NO COMPOUNDING ASSERTION:
        // D should start on 2026-06-13 (shifted exactly +3 days from 2026-06-10), NOT +6 days!
        assertThat(delayedD.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(delayedD.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 14));

        long dShift = ChronoUnit.DAYS.between(refD.getScheduledStartDate(), delayedD.getScheduledStartDate());
        assertThat(dShift).as("Shift on converging task D must be exactly 3 days").isEqualTo(3);

        // =========================================================================
        // STEP 5: Baseline Recovery (Shift A Back to 2026-06-01)
        // =========================================================================
        schedulingService.updateTaskSchedule(delayedA, LocalDate.of(2026, 6, 1), delayedA.getDurationDays());

        Task restoredA = taskRepository.findById(taskA.getId()).orElseThrow();
        Task restoredB = taskRepository.findById(taskB.getId()).orElseThrow();
        Task restoredC = taskRepository.findById(taskC.getId()).orElseThrow();
        Task restoredD = taskRepository.findById(taskD.getId()).orElseThrow();

        assertThat(restoredA.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(restoredB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(restoredC.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(restoredD.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(restoredD.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 11));

        // =========================================================================
        // STEP 6: AI Suggestion & Explicit Human Acceptance
        // =========================================================================
        Task taskE = taskRepository.saveAndFlush(new Task(
                project, "Task E - Security Compliance Audit", "Final compliance certification",
                TaskStatus.BACKLOG, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 2
        ));

        // Explicit human accepts suggestion: D is prerequisite to E (D -> E)
        suggestionService.acceptSuggestion(new AcceptSuggestionRequest(taskD.getId(), taskE.getId()));

        Task postAcceptedE = taskRepository.findById(taskE.getId()).orElseThrow();
        // E should now be scheduled after D finishes on 2026-06-11 -> E starts 2026-06-12
        assertThat(postAcceptedE.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(postAcceptedE.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(postAcceptedE.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Verify new CPM includes E
        CriticalPathResponse updatedCpm = criticalPathService.calculateCriticalPath(project.getId());
        assertThat(updatedCpm.criticalTaskIds()).contains(taskE.getId());
        assertThat(updatedCpm.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 13));
    }
}
