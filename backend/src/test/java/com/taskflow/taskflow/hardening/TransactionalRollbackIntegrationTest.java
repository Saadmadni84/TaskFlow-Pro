package com.taskflow.taskflow.hardening;

import com.taskflow.taskflow.common.exception.CycleDetectedException;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransactionalRollbackIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private TaskDependencyService dependencyService;

    private Project project;
    private Task taskA;
    private Task taskB;
    private Task taskC;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(new Project("Rollback Test Project", "Verifying atomic transactions"));

        taskA = taskRepository.saveAndFlush(new Task(project, "Task A", "First", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), 5));
        taskB = taskRepository.saveAndFlush(new Task(project, "Task B", "Second", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 8), 3));
        taskC = taskRepository.saveAndFlush(new Task(project, "Task C", "Third", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 12), 4));

        // Create A -> B
        dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskB.getId()));
        // Create B -> C
        dependencyService.createDependency(new CreateDependencyRequest(taskB.getId(), taskC.getId()));
    }

    @Test
    @DisplayName("Cycle creation attempts must fail and cleanly roll back without inserting illegal dependency")
    void shouldRollbackCleanlyWhenCycleDetected() {
        long dependencyCountBefore = dependencyRepository.count();
        assertThat(dependencyCountBefore).isEqualTo(2);

        // Attempt to create C -> A (would introduce cycle A -> B -> C -> A)
        CreateDependencyRequest cyclicRequest = new CreateDependencyRequest(taskC.getId(), taskA.getId());

        assertThatThrownBy(() -> dependencyService.createDependency(cyclicRequest))
                .isInstanceOf(CycleDetectedException.class);

        // Verify count remains strictly 2; no orphaned or uncommitted edge persisted
        long dependencyCountAfter = dependencyRepository.count();
        assertThat(dependencyCountAfter).isEqualTo(dependencyCountBefore);

        List<TaskDependency> allDeps = dependencyRepository.findAll();
        assertThat(allDeps).noneMatch(d ->
                d.getPredecessor().getId().equals(taskC.getId()) &&
                d.getSuccessor().getId().equals(taskA.getId())
        );

        // Verify tasks still retain valid states
        Task refA = taskRepository.findById(taskA.getId()).orElseThrow();
        Task refB = taskRepository.findById(taskB.getId()).orElseThrow();
        Task refC = taskRepository.findById(taskC.getId()).orElseThrow();

        assertThat(refA.getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(refB.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
        assertThat(refC.getDependencyStatus()).isEqualTo(DependencyStatus.BLOCKED);
    }

    @Test
    @DisplayName("Self dependency attempts must fail without modifying database")
    void shouldRollbackWhenSelfDependencyAttempted() {
        long countBefore = dependencyRepository.count();

        assertThatThrownBy(() -> dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskA.getId())))
                .isInstanceOf(com.taskflow.taskflow.common.exception.SelfDependencyException.class);

        assertThat(dependencyRepository.count()).isEqualTo(countBefore);
    }
}
