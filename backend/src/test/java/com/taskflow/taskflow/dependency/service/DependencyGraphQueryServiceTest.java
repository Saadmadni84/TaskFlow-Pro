package com.taskflow.taskflow.dependency.service;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.dependency.dto.DependencyGraphDto;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyGraphQueryServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskDependencyRepository dependencyRepository;

    @InjectMocks
    private DependencyGraphQueryService queryService;

    private UUID projectId;
    private Project project;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        project = new Project(projectId, "Test Project", "Description");
    }

    @Test
    @DisplayName("Should throw ProjectNotFoundException when project does not exist")
    void shouldThrowWhenProjectNotFound() {
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThatThrownBy(() -> queryService.getProjectDependencyGraph(projectId))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    @DisplayName("Should return empty nodes and edges for empty project")
    void shouldReturnEmptyGraphForEmptyProject() {
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(dependencyRepository.findByProjectId(projectId)).thenReturn(List.of());

        DependencyGraphDto result = queryService.getProjectDependencyGraph(projectId);

        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
    }

    @Test
    @DisplayName("Should return isolated tasks without edges")
    void shouldReturnIsolatedTasks() {
        Task taskA = new Task(project, "Task A", "Desc", TaskStatus.BACKLOG, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 3);
        Task taskB = new Task(project, "Task B", "Desc", TaskStatus.BACKLOG, LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 6), 3);

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(projectId)).thenReturn(List.of());

        DependencyGraphDto result = queryService.getProjectDependencyGraph(projectId);

        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).isEmpty();
        assertThat(result.nodes()).extracting("title").containsExactlyInAnyOrder("Task A", "Task B");
    }

    @Test
    @DisplayName("Should return diamond converging graph preserving edge direction")
    void shouldReturnDiamondGraphWithCorrectDirections() {
        Task taskA = new Task(project, "Task A", "Desc", TaskStatus.DONE, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 3);
        Task taskB = new Task(project, "Task B", "Desc", TaskStatus.IN_PROGRESS, LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 6), 3);
        Task taskC = new Task(project, "Task C", "Desc", TaskStatus.BACKLOG, LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 7), 4);
        Task taskD = new Task(project, "Task D", "Desc", TaskStatus.BACKLOG, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 9), 2);

        TaskDependency depAB = new TaskDependency(UUID.randomUUID(), taskA, taskB);
        TaskDependency depAC = new TaskDependency(UUID.randomUUID(), taskA, taskC);
        TaskDependency depBD = new TaskDependency(UUID.randomUUID(), taskB, taskD);
        TaskDependency depCD = new TaskDependency(UUID.randomUUID(), taskC, taskD);

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(taskA, taskB, taskC, taskD));
        when(dependencyRepository.findByProjectId(projectId)).thenReturn(List.of(depAB, depAC, depBD, depCD));

        DependencyGraphDto result = queryService.getProjectDependencyGraph(projectId);

        assertThat(result.nodes()).hasSize(4);
        assertThat(result.edges()).hasSize(4);

        // Verify edge predecessor -> successor direction
        assertThat(result.edges()).anySatisfy(e -> {
            assertThat(e.predecessorTaskId()).isEqualTo(taskA.getId());
            assertThat(e.successorTaskId()).isEqualTo(taskB.getId());
        });
        assertThat(result.edges()).anySatisfy(e -> {
            assertThat(e.predecessorTaskId()).isEqualTo(taskA.getId());
            assertThat(e.successorTaskId()).isEqualTo(taskC.getId());
        });
        assertThat(result.edges()).anySatisfy(e -> {
            assertThat(e.predecessorTaskId()).isEqualTo(taskB.getId());
            assertThat(e.successorTaskId()).isEqualTo(taskD.getId());
        });
        assertThat(result.edges()).anySatisfy(e -> {
            assertThat(e.predecessorTaskId()).isEqualTo(taskC.getId());
            assertThat(e.successorTaskId()).isEqualTo(taskD.getId());
        });
    }
}
