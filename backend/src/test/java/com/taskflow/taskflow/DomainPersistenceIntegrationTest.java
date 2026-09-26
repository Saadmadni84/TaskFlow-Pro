package com.taskflow.taskflow;

import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DomainPersistenceIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    private Project savedProject;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        Project project = new Project("Persistence Test Project", "Testing PostgreSQL domain constraints");
        savedProject = projectRepository.saveAndFlush(project);
    }

    @Test
    @DisplayName("Should persist and retrieve project with timestamps")
    void shouldPersistAndRetrieveProject() {
        assertThat(savedProject.getId()).isNotNull();
        assertThat(savedProject.getCreatedAt()).isNotNull();
        assertThat(savedProject.getUpdatedAt()).isNotNull();

        Project retrieved = projectRepository.findById(savedProject.getId()).orElseThrow();
        assertThat(retrieved.getName()).isEqualTo("Persistence Test Project");
        assertThat(retrieved.getDescription()).isEqualTo("Testing PostgreSQL domain constraints");
    }

    @Test
    @DisplayName("Should persist and retrieve task with optimistic locking and full metadata")
    void shouldPersistAndRetrieveTask() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate due = LocalDate.of(2026, 10, 15);

        Task task = new Task(
                savedProject,
                "Implement DAG Engine",
                "Cycle detection and topological sort",
                TaskStatus.IN_PROGRESS,
                start,
                due,
                14
        );

        Task savedTask = taskRepository.saveAndFlush(task);

        assertThat(savedTask.getId()).isNotNull();
        assertThat(savedTask.getVersion()).isEqualTo(0L);
        assertThat(savedTask.getWorkflowStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(savedTask.getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(savedTask.getStartDate()).isEqualTo(start);
        assertThat(savedTask.getDueDate()).isEqualTo(due);
        assertThat(savedTask.getDurationDays()).isEqualTo(14);
        assertThat(savedTask.getCreatedAt()).isNotNull();
        assertThat(savedTask.getUpdatedAt()).isNotNull();

        // Update task and verify optimistic locking increment
        savedTask.setTitle("Implement DAG Engine (Updated)");
        Task updatedTask = taskRepository.saveAndFlush(savedTask);
        assertThat(updatedTask.getVersion()).isEqualTo(1L);
        assertThat(updatedTask.getTitle()).isEqualTo("Implement DAG Engine (Updated)");
    }

    @Test
    @DisplayName("Should persist directed dependency relationship between tasks")
    void shouldPersistDirectedDependency() {
        Task taskA = taskRepository.saveAndFlush(new Task(savedProject, "Task A", "Predecessor", TaskStatus.DONE, null, null, null));
        Task taskB = taskRepository.saveAndFlush(new Task(savedProject, "Task B", "Successor", TaskStatus.BACKLOG, null, null, null));

        TaskDependency dependency = new TaskDependency(taskA, taskB);
        TaskDependency savedDependency = dependencyRepository.saveAndFlush(dependency);

        assertThat(savedDependency.getId()).isNotNull();
        assertThat(savedDependency.getCreatedAt()).isNotNull();

        List<TaskDependency> outgoing = dependencyRepository.findByPredecessorId(taskA.getId());
        assertThat(outgoing).hasSize(1);
        assertThat(outgoing.get(0).getSuccessor().getId()).isEqualTo(taskB.getId());

        List<TaskDependency> incoming = dependencyRepository.findBySuccessorId(taskB.getId());
        assertThat(incoming).hasSize(1);
        assertThat(incoming.get(0).getPredecessor().getId()).isEqualTo(taskA.getId());
    }

    @Test
    @DisplayName("Should enforce database unique constraint on duplicate dependency edges")
    void shouldRejectDuplicateDependencyAtDatabaseLevel() {
        Task taskA = taskRepository.saveAndFlush(new Task(savedProject, "Task A", "Pred", TaskStatus.DONE, null, null, null));
        Task taskB = taskRepository.saveAndFlush(new Task(savedProject, "Task B", "Succ", TaskStatus.BACKLOG, null, null, null));

        TaskDependency dep1 = new TaskDependency(taskA, taskB);
        dependencyRepository.saveAndFlush(dep1);

        TaskDependency dep2 = new TaskDependency(taskA, taskB);
        assertThatThrownBy(() -> dependencyRepository.saveAndFlush(dep2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
