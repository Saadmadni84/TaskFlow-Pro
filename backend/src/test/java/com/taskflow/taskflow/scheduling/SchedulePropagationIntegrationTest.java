package com.taskflow.taskflow.scheduling;

import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.scheduling.service.SchedulingService;
import com.taskflow.taskflow.task.dto.UpdateTaskRequest;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchedulePropagationIntegrationTest {

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

    private Project project;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(new Project("Scheduling Integration Project", "Verifying DAG scheduling engine"));
    }

    private Task createTask(String title, LocalDate plannedStart, int durationDays) {
        LocalDate due = plannedStart != null ? plannedStart.plusDays(durationDays - 1) : null;
        Task task = new Task(project, title, "Description", TaskStatus.BACKLOG, plannedStart, due, durationDays);
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
    @DisplayName("Test 1: Root task schedules at planned start with duration preserved")
    void shouldScheduleRootTaskCorrectly() {
        LocalDate start = LocalDate.of(2026, 6, 10);
        Task root = createTask("Root", start, 3);

        Task reloaded = reload(root);
        assertThat(reloaded.getPlannedStartDate()).isEqualTo(start);
        assertThat(reloaded.getScheduledStartDate()).isEqualTo(start);
        assertThat(reloaded.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(reloaded.getDurationDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("Test 2: Single predecessor forces successor later (A due June 10, B planned June 5 -> B starts June 11)")
    void shouldShiftSuccessorWhenPredecessorFinishesLater() {
        Task taskA = createTask("Task A", LocalDate.of(2026, 6, 8), 3); // due June 10
        Task taskB = createTask("Task B", LocalDate.of(2026, 6, 5), 3); // planned June 5

        link(taskA, taskB);

        Task reloadedB = reload(taskB);
        assertThat(reloadedB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(reloadedB.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(reloadedB.getDurationDays()).isEqualTo(3);
        assertThat(reloadedB.getPlannedStartDate()).isEqualTo(LocalDate.of(2026, 6, 5));
    }

    @Test
    @DisplayName("Test 3: Planned date already later than constraint is not pulled earlier")
    void shouldNotPullTaskEarlierWhenPlannedDateIsAlreadyLater() {
        Task taskA = createTask("Task A", LocalDate.of(2026, 6, 8), 3); // due June 10
        Task taskB = createTask("Task B", LocalDate.of(2026, 6, 15), 3); // planned June 15

        link(taskA, taskB);

        Task reloadedB = reload(taskB);
        assertThat(reloadedB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(reloadedB.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 17));
    }

    @Test
    @DisplayName("Test 4: Multiple predecessors respect maximum required start constraint")
    void shouldRespectMaximumConstraintAmongMultiplePredecessors() {
        Task taskA = createTask("Task A", LocalDate.of(2026, 6, 8), 3);  // due June 10 -> req start June 11
        Task taskB = createTask("Task B", LocalDate.of(2026, 6, 8), 5);  // due June 12 -> req start June 13
        Task taskC = createTask("Task C", LocalDate.of(2026, 6, 8), 4);  // due June 11 -> req start June 12
        Task taskD = createTask("Task D", LocalDate.of(2026, 6, 1), 4);  // planned June 1

        link(taskA, taskD);
        link(taskB, taskD);
        link(taskC, taskD);

        Task reloadedD = reload(taskD);
        assertThat(reloadedD.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(reloadedD.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(reloadedD.getDurationDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("Test 5 (MANDATORY REGRESSION): Converging paths (A -> B -> D, A -> C -> D) do NOT compound delay")
    void shouldNotCompoundDelaysInConvergingGraph() {
        // Initial setup:
        // A: June 1 -> June 3 (due June 3, duration 3)
        // B: June 4 -> June 5 (due June 5, duration 2)
        // C: June 4 -> June 5 (due June 5, duration 2)
        // D: June 6 -> June 8 (due June 8, duration 3)
        Task taskA = createTask("Task A", LocalDate.of(2026, 6, 1), 3);
        Task taskB = createTask("Task B", LocalDate.of(2026, 6, 4), 2);
        Task taskC = createTask("Task C", LocalDate.of(2026, 6, 4), 2);
        Task taskD = createTask("Task D", LocalDate.of(2026, 6, 6), 3);

        link(taskA, taskB);
        link(taskA, taskC);
        link(taskB, taskD);
        link(taskC, taskD);

        Task initD = reload(taskD);
        assertThat(initD.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(initD.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 8));

        // Action: Shift A by +3 days (June 1 -> June 4, due June 6)
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 4), null, 3
        ));

        // Assertions:
        Task reloadedA = reload(taskA);
        assertThat(reloadedA.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(reloadedA.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 6));

        Task reloadedB = reload(taskB);
        assertThat(reloadedB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(reloadedB.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 8));

        Task reloadedC = reload(taskC);
        assertThat(reloadedC.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(reloadedC.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 8));

        Task reloadedD = reload(taskD);
        // D must start on June 9, NOT June 12!
        // Shift is exactly 3 days (June 6 to June 9), proving NO COMPOUNDING!
        long shiftDays = ChronoUnit.DAYS.between(initD.getScheduledStartDate(), reloadedD.getScheduledStartDate());
        assertThat(shiftDays).isEqualTo(3);

        assertThat(reloadedD.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(reloadedD.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(reloadedD.getDurationDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("Test 6: Multi-level chain (A -> B -> C -> D -> E) propagates sequentially")
    void shouldPropagateSequentiallyThroughMultiLevelChain() {
        Task taskA = createTask("A", LocalDate.of(2026, 6, 1), 2); // June 1 - 2
        Task taskB = createTask("B", LocalDate.of(2026, 6, 1), 2); // June 3 - 4
        Task taskC = createTask("C", LocalDate.of(2026, 6, 1), 2); // June 5 - 6
        Task taskD = createTask("D", LocalDate.of(2026, 6, 1), 2); // June 7 - 8
        Task taskE = createTask("E", LocalDate.of(2026, 6, 1), 2); // June 9 - 10

        link(taskA, taskB);
        link(taskB, taskC);
        link(taskC, taskD);
        link(taskD, taskE);

        assertThat(reload(taskB).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(reload(taskC).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(reload(taskD).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(reload(taskE).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 9));

        // Move A +5 days: June 1 -> June 6
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 6), null, 2
        ));

        // A is June 6 - 7
        assertThat(reload(taskA).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(reload(taskA).getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 7));

        // B: June 8 - 9
        assertThat(reload(taskB).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        // C: June 10 - 11
        assertThat(reload(taskC).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        // D: June 12 - 13
        assertThat(reload(taskD).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        // E: June 14 - 15
        assertThat(reload(taskE).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 14));
    }

    @Test
    @DisplayName("Test 7: Branching graph (A -> B, A -> C, A -> D) recalculates all branches independently")
    void shouldRecalculateAllBranchesIndependently() {
        Task taskA = createTask("A", LocalDate.of(2026, 6, 1), 3); // due June 3
        Task taskB = createTask("B", LocalDate.of(2026, 6, 1), 2); // req start June 4
        Task taskC = createTask("C", LocalDate.of(2026, 6, 1), 5); // req start June 4
        Task taskD = createTask("D", LocalDate.of(2026, 6, 1), 1); // req start June 4

        link(taskA, taskB);
        link(taskA, taskC);
        link(taskA, taskD);

        assertThat(reload(taskB).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(reload(taskC).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(reload(taskD).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 4));

        // Shift A by +4 days -> due June 7
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 5), null, 3
        ));

        // All 3 children start June 8
        assertThat(reload(taskB).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(reload(taskB).getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 9));

        assertThat(reload(taskC).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(reload(taskC).getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 12));

        assertThat(reload(taskD).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(reload(taskD).getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 8));
    }

    @Test
    @DisplayName("Test 8: Unrelated graph components remain unchanged when another component is shifted")
    void shouldNotAffectUnrelatedGraphComponents() {
        // Component 1: A -> B
        Task taskA = createTask("A", LocalDate.of(2026, 6, 1), 3);
        Task taskB = createTask("B", LocalDate.of(2026, 6, 1), 3);
        link(taskA, taskB);

        // Component 2: X -> Y
        Task taskX = createTask("X", LocalDate.of(2026, 7, 1), 4);
        Task taskY = createTask("Y", LocalDate.of(2026, 7, 1), 4);
        link(taskX, taskY);

        Task initY = reload(taskY);
        Long versionYBefore = initY.getVersion();
        LocalDate startYBefore = initY.getScheduledStartDate();

        // Shift A
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 10), null, 3
        ));

        // Verify Component 2 is completely untouched (no date change, no version increment)
        Task finalY = reload(taskY);
        assertThat(finalY.getScheduledStartDate()).isEqualTo(startYBefore);
        assertThat(finalY.getVersion()).isEqualTo(versionYBefore);
    }

    @Test
    @DisplayName("Test 9: Earlier upstream change allows tasks to return to their planned start baseline")
    void shouldReturnToPlannedStartWhenUpstreamTaskMovesEarlier() {
        Task taskA = createTask("A", LocalDate.of(2026, 6, 10), 5); // due June 14
        Task taskB = createTask("B", LocalDate.of(2026, 6, 10), 3); // planned June 10

        link(taskA, taskB);
        // B was pushed to June 15
        assertThat(reload(taskB).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));

        // Now move A earlier: planned start June 1, duration 5 -> due June 5
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 1), null, 5
        ));

        // Constraint is now June 6 <= planned start June 10 -> B returns to June 10
        Task reloadedB = reload(taskB);
        assertThat(reloadedB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(reloadedB.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 12));
    }

    @Test
    @DisplayName("Test 10: Dependency creation and removal recalculate successor schedule")
    void shouldRecalculateScheduleOnDependencyAddAndRemove() {
        Task taskA = createTask("A", LocalDate.of(2026, 6, 10), 5); // due June 14
        Task taskB = createTask("B", LocalDate.of(2026, 6, 5), 3);  // planned June 5

        // Initially B has no dependencies -> scheduled June 5
        assertThat(reload(taskB).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 5));

        // Add A -> B -> B must shift to June 15
        link(taskA, taskB);
        assertThat(reload(taskB).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));

        // Remove A -> B -> B returns to planned start June 5
        unlink(taskA, taskB);
        assertThat(reload(taskB).getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 5));
    }

    @Test
    @DisplayName("Test 11: Idempotency and No-op (updating to same dates causes no version churn)")
    void shouldBeIdempotentWithoutUnnecessaryVersionIncrements() {
        Task taskA = createTask("A", LocalDate.of(2026, 6, 1), 3);
        Task taskB = createTask("B", LocalDate.of(2026, 6, 1), 3);
        link(taskA, taskB);

        Task initialB = reload(taskB);
        Long initialVersion = initialB.getVersion();

        // Update A with exact same start date and duration
        taskService.updateTask(taskA.getId(), new UpdateTaskRequest(
                taskA.getTitle(), taskA.getDescription(), TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 1), null, 3
        ));

        Task finalB = reload(taskB);
        assertThat(finalB.getVersion()).isEqualTo(initialVersion);
    }

    @Test
    @DisplayName("Test 12: Invariant check across entire project graph")
    void shouldMaintainSchedulingInvariantsAcrossProject() {
        Task t1 = createTask("T1", LocalDate.of(2026, 6, 1), 3);
        Task t2 = createTask("T2", LocalDate.of(2026, 6, 2), 2);
        Task t3 = createTask("T3", LocalDate.of(2026, 6, 1), 4);
        Task t4 = createTask("T4", LocalDate.of(2026, 6, 1), 3);

        link(t1, t2);
        link(t2, t4);
        link(t3, t4);

        // Recalculate for entire project
        schedulingService.recalculateForProject(project.getId());

        List<Task> allTasks = taskRepository.findByProjectId(project.getId());
        for (Task task : allTasks) {
            // Invariant 1: scheduledDue = scheduledStart + duration - 1
            if (task.getScheduledStartDate() != null) {
                assertThat(task.getScheduledDueDate())
                        .isEqualTo(task.getScheduledStartDate().plusDays(task.getDurationDays() - 1));
            }

            // Invariant 2: scheduledStart >= plannedStart
            if (task.getPlannedStartDate() != null && task.getScheduledStartDate() != null) {
                assertThat(task.getScheduledStartDate())
                        .isAfterOrEqualTo(task.getPlannedStartDate());
            }

            // Invariant 3: successor.scheduledStart >= predecessor.scheduledDue + 1
            List<Task> predecessors = dependencyRepository.findBySuccessorId(task.getId()).stream()
                    .map(dep -> reload(dep.getPredecessor()))
                    .toList();

            for (Task pred : predecessors) {
                if (pred.getScheduledDueDate() != null && task.getScheduledStartDate() != null) {
                    assertThat(task.getScheduledStartDate())
                            .isAfterOrEqualTo(pred.getScheduledDueDate().plusDays(1));
                }
            }
        }
    }
}
