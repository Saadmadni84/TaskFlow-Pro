package com.taskflow.taskflow.dependency.readiness;

import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DependencyReadinessIntegrationTest {

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
    private DependencyReadinessService readinessService;

    private Project project;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(new Project("Readiness Test Project", "Verifying DAG Readiness Engine"));
    }

    private Task createTask(String title, TaskStatus workflowStatus) {
        Task task = new Task(project, title, "Test description", workflowStatus, null, null, null);
        return taskRepository.saveAndFlush(task);
    }

    private void link(Task predecessor, Task successor) {
        dependencyService.createDependency(new CreateDependencyRequest(predecessor.getId(), successor.getId()));
    }

    private void unlink(Task predecessor, Task successor) {
        dependencyService.deleteDependency(predecessor.getId(), successor.getId());
    }

    private Task reload(Task task) {
        return taskRepository.findById(task.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Test 1: Basic dependency (A -> B, A is IN_PROGRESS -> B is BLOCKED)")
    void shouldSetSuccessorToBlockedWhenPredecessorIsInProgress() {
        Task taskA = createTask("Task A", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", TaskStatus.BACKLOG);

        link(taskA, taskB);

        assertThat(reload(taskA).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
    }

    @Test
    @DisplayName("Test 2: Unlock (A -> B, A becomes DONE -> B becomes READY)")
    void shouldUnlockSuccessorToReadyWhenPredecessorBecomesDone() {
        Task taskA = createTask("Task A", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", TaskStatus.BACKLOG);

        link(taskA, taskB);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Transition A to DONE
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.DONE, null, null, null
        ));

        assertThat(reload(taskA).getWorkflowStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Test 3: Multi-level unlock (A -> B -> C: A DONE -> B READY, C BLOCKED; B DONE -> C READY)")
    void shouldPerformMultiLevelUnlockAsTasksComplete() {
        Task taskA = createTask("Task A", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", TaskStatus.BACKLOG);
        Task taskC = createTask("Task C", TaskStatus.BACKLOG);

        link(taskA, taskB);
        link(taskB, taskC);

        assertThat(reload(taskA).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        assertThat(reload(taskC).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Step 1: Set A to DONE -> B becomes READY, C remains BLOCKED
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.DONE, null, null, null
        ));

        assertThat(reload(taskA).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskC).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Step 2: Set B to DONE -> C becomes READY
        taskService.updateTask(taskB.getId(), new UpdateTaskRequest(
                taskB.getTitle(), taskB.getDescription(), TaskStatus.DONE, null, null, null
        ));

        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskC).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Test 4 (Section 35 Mandatory): Rollback (A -> B -> C: A and B DONE, C READY -> A reopened to IN_PROGRESS -> B and C become BLOCKED)")
    void shouldPropagateRollbackAcrossDownstreamDescendants() {
        // Initial setup: A=DONE, B=DONE, C=BACKLOG (READY)
        Task taskA = createTask("Task A", TaskStatus.DONE);
        Task taskB = createTask("Task B", TaskStatus.DONE);
        Task taskC = createTask("Task C", TaskStatus.BACKLOG);

        link(taskA, taskB);
        link(taskB, taskC);

        // Verify initial state
        assertThat(reload(taskA).getWorkflowStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(reload(taskB).getWorkflowStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(reload(taskA).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskC).getDependencyStatus()).isEqualTo(DependencyStatus.READY);

        // Action: Reopen A to IN_PROGRESS
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.IN_PROGRESS, null, null, null
        ));

        // Downstream recalculation: B must become BLOCKED, and C must become BLOCKED
        assertThat(reload(taskA).getWorkflowStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reload(taskA).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        assertThat(reload(taskC).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
    }

    @Test
    @DisplayName("Test 5 (Section 36): Converging graph (A -> B -> D, A -> C -> D)")
    void shouldHandleConvergingGraphWithoutCompoundingOrDuplicateEvaluation() {
        Task taskA = createTask("Task A", TaskStatus.DONE);
        Task taskB = createTask("Task B", TaskStatus.DONE);
        Task taskC = createTask("Task C", TaskStatus.IN_PROGRESS);
        Task taskD = createTask("Task D", TaskStatus.BACKLOG);

        link(taskA, taskB);
        link(taskA, taskC);
        link(taskB, taskD);
        link(taskC, taskD);

        // Since C is IN_PROGRESS, D must be BLOCKED
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskC).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(reload(taskD).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Complete C: C -> DONE. Now both B and C are DONE, so D must become READY
        taskService.updateTask(taskC.getId(), new UpdateTaskRequest(
                taskC.getTitle(), taskC.getDescription(), TaskStatus.DONE, null, null, null
        ));

        assertThat(reload(taskC).getWorkflowStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(reload(taskD).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Test 6 (Section 37): Multiple prerequisites regression (A -> D, B -> D: A reopened -> D BLOCKED, A re-completed -> D READY)")
    void shouldHandleMultiplePrerequisitesRegression() {
        Task taskA = createTask("Task A", TaskStatus.DONE);
        Task taskB = createTask("Task B", TaskStatus.DONE);
        Task taskD = createTask("Task D", TaskStatus.BACKLOG);

        link(taskA, taskD);
        link(taskB, taskD);

        assertThat(reload(taskD).getDependencyStatus()).isEqualTo(DependencyStatus.READY);

        // Change A to IN_PROGRESS -> D must become BLOCKED
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.IN_PROGRESS, null, null, null
        ));
        assertThat(reload(taskD).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Change A back to DONE -> D must become READY
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.DONE, null, null, null
        ));
        assertThat(reload(taskD).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Test 7 (Section 38): Dependency removal (A -> B with A IN_PROGRESS: remove A -> B -> B becomes READY)")
    void shouldRecalculateToReadyWhenSingleDependencyRemoved() {
        Task taskA = createTask("Task A", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", TaskStatus.BACKLOG);

        link(taskA, taskB);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        unlink(taskA, taskB);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Test 8 (Section 39): Converging dependency removal (A -> D, B -> D with A IN_PROGRESS, B DONE: remove A -> D -> D becomes READY)")
    void shouldRecalculateToReadyWhenBlockingEdgeRemovedFromConvergingGraph() {
        Task taskA = createTask("Task A", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", TaskStatus.DONE);
        Task taskD = createTask("Task D", TaskStatus.BACKLOG);

        link(taskA, taskD);
        link(taskB, taskD);
        assertThat(reload(taskD).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Remove blocking prerequisite A -> D
        unlink(taskA, taskD);

        // D now only depends on B, which is DONE -> D must become READY
        assertThat(reload(taskD).getDependencyStatus()).isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Test 9 (Section 40): No-op (A -> B with A IN_PROGRESS -> BACKLOG does not trigger recalculation)")
    void shouldNotTriggerDownstreamRecalculationForNonDoneToNonDoneTransitions() {
        Task taskA = createTask("Task A", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", TaskStatus.BACKLOG);

        link(taskA, taskB);

        Task reloadedB = reload(taskB);
        assertThat(reloadedB.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        Long versionBefore = reloadedB.getVersion();

        // Transition A: IN_PROGRESS -> BACKLOG (neither is DONE)
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.BACKLOG, null, null, null
        ));

        Task reloadedBAfter = reload(taskB);
        assertThat(reloadedBAfter.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        // Verify task B was not touched, saving unnecessary DB writes and optimistic-lock version churn
        assertThat(reloadedBAfter.getVersion()).isEqualTo(versionBefore);
    }

    @Test
    @DisplayName("Test 10 (Section 41): Idempotency (calling recalculateForTask twice without state changes produces same result without version churn)")
    void shouldBeIdempotentWithoutUnnecessaryVersionIncrements() {
        Task taskA = createTask("Task A", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", TaskStatus.BACKLOG);

        link(taskA, taskB);

        Task initialB = reload(taskB);
        assertThat(initialB.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        Long initialVersion = initialB.getVersion();

        // First recalculation
        List<Task> firstRun = readinessService.recalculateForTask(taskA.getId());
        assertThat(firstRun).isEmpty(); // No state changed

        // Second recalculation
        List<Task> secondRun = readinessService.recalculateForTask(taskA.getId());
        assertThat(secondRun).isEmpty();

        Task finalB = reload(taskB);
        assertThat(finalB.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        assertThat(finalB.getVersion()).isEqualTo(initialVersion);
    }

    @Test
    @DisplayName("Test 11 (Section 42): State consistency invariant holds across arbitrary project graph")
    void shouldMaintainStateConsistencyInvariantAcrossArbitraryGraph() {
        Task t1 = createTask("T1", TaskStatus.DONE);
        Task t2 = createTask("T2", TaskStatus.DONE);
        Task t3 = createTask("T3", TaskStatus.IN_PROGRESS);
        Task t4 = createTask("T4", TaskStatus.BACKLOG);

        link(t1, t2);
        link(t2, t4);
        link(t3, t4);

        // Recalculate for entire project
        readinessService.recalculateForProject(project.getId());

        List<Task> projectTasks = taskRepository.findByProjectId(project.getId());

        for (Task task : projectTasks) {
            List<Task> predecessors = dependencyRepository.findBySuccessorId(task.getId()).stream()
                    .map(dep -> reload(dep.getPredecessor()))
                    .toList();

            boolean allPredecessorsSatisfied = predecessors.isEmpty() || predecessors.stream()
                    .allMatch(p -> p.getWorkflowStatus() == TaskStatus.DONE && p.getDependencyStatus() == DependencyStatus.READY);

            if (allPredecessorsSatisfied) {
                assertThat(task.getDependencyStatus())
                        .as("Task [%s] should be READY", task.getTitle())
                        .isEqualTo(DependencyStatus.READY);
            } else {
                assertThat(task.getDependencyStatus())
                        .as("Task [%s] should be BLOCKED", task.getTitle())
                        .isEqualTo(DependencyStatus.BLOCKED);
            }
        }
    }

    @Test
    @DisplayName("Should reject transition to DONE when task is BLOCKED by incomplete prerequisites")
    void shouldRejectDoneTransitionWhenBlocked() {
        Task taskA = createTask("Task A", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", TaskStatus.BACKLOG);
        link(taskA, taskB);

        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Attempting to complete Task B while Task A is IN_PROGRESS must fail with 409 / BlockedTaskCompletionException
        assertThatThrownBy(() -> taskService.updateTask(taskB.getId(),
                new UpdateTaskRequest("Task B", "Desc", TaskStatus.DONE, null, null, null)))
                .isInstanceOf(com.taskflow.taskflow.common.exception.BlockedTaskCompletionException.class)
                .hasMessageContaining("Cannot mark task 'Task B' as DONE: task is BLOCKED");

        // Verify Task B remained BACKLOG and BLOCKED
        assertThat(reload(taskB).getWorkflowStatus()).isEqualTo(TaskStatus.BACKLOG);
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);

        // Now complete Task A -> Task B automatically becomes READY
        taskService.updateTask(taskA.getId(),
                new UpdateTaskRequest("Task A", "Desc", TaskStatus.DONE, null, null, null));
        assertThat(reload(taskB).getDependencyStatus()).isEqualTo(DependencyStatus.READY);

        // Now completing Task B succeeds
        taskService.updateTask(taskB.getId(),
                new UpdateTaskRequest("Task B", "Desc", TaskStatus.DONE, null, null, null));
        assertThat(reload(taskB).getWorkflowStatus()).isEqualTo(TaskStatus.DONE);
    }
}
