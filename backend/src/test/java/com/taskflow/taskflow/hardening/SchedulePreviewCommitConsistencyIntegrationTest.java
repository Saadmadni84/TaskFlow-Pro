package com.taskflow.taskflow.hardening;

import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.scheduling.dto.ScheduleImpactPreviewResponse;
import com.taskflow.taskflow.scheduling.dto.SchedulePreviewRequest;
import com.taskflow.taskflow.scheduling.dto.TaskScheduleImpactDto;
import com.taskflow.taskflow.scheduling.service.ScheduleImpactPreviewService;
import com.taskflow.taskflow.scheduling.service.SchedulingService;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchedulePreviewCommitConsistencyIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private TaskDependencyService dependencyService;

    @Autowired
    private ScheduleImpactPreviewService previewService;

    @Autowired
    private SchedulingService schedulingService;

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

        project = projectRepository.saveAndFlush(new Project("Preview Consistency Project", "Testing preview vs committed consistency"));

        // Graph: A -> B -> D and A -> C -> D (Diamond)
        taskA = taskRepository.saveAndFlush(new Task(project, "Task A", "Root", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), 5));
        taskB = taskRepository.saveAndFlush(new Task(project, "Task B", "Branch 1", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 8), 3));
        taskC = taskRepository.saveAndFlush(new Task(project, "Task C", "Branch 2", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 9), 4));
        taskD = taskRepository.saveAndFlush(new Task(project, "Task D", "Sink", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11), 2));

        dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskB.getId()));
        dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskC.getId()));
        dependencyService.createDependency(new CreateDependencyRequest(taskB.getId(), taskD.getId()));
        dependencyService.createDependency(new CreateDependencyRequest(taskC.getId(), taskD.getId()));
    }

    @Test
    @DisplayName("Simulated schedule preview matches committed schedule state 100% across all tasks")
    void shouldMatchPreviewAndCommittedSchedule() {
        LocalDate proposedDate = LocalDate.of(2026, 6, 4); // Shift A forward by 3 days

        // 1. Calculate side-effect-free preview
        ScheduleImpactPreviewResponse preview = previewService.calculatePreview(
                taskA.getId(),
                new SchedulePreviewRequest(proposedDate)
        );

        assertThat(preview.tasks()).hasSize(4);
        Map<UUID, TaskScheduleImpactDto> previewMap = preview.tasks().stream()
                .collect(Collectors.toMap(TaskScheduleImpactDto::taskId, dto -> dto));

        // 2. Commit the exact same schedule change
        Task freshA = taskRepository.findById(taskA.getId()).orElseThrow();
        schedulingService.updateTaskSchedule(freshA, proposedDate, freshA.getDurationDays());

        // 3. Verify against actual committed database state
        for (UUID id : previewMap.keySet()) {
            Task committed = taskRepository.findById(id).orElseThrow();
            TaskScheduleImpactDto sim = previewMap.get(id);

            assertThat(committed.getScheduledStartDate())
                    .as("Scheduled start date for task " + committed.getTitle())
                    .isEqualTo(sim.proposedScheduledStart());

            assertThat(committed.getScheduledDueDate())
                    .as("Scheduled due date for task " + committed.getTitle())
                    .isEqualTo(sim.proposedScheduledDue());
        }
    }
}
