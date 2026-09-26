package com.taskflow.taskflow.dependency.service;

import com.taskflow.taskflow.common.exception.CycleDetectedException;
import com.taskflow.taskflow.common.exception.DuplicateDependencyException;
import com.taskflow.taskflow.common.exception.InvalidDependencyException;
import com.taskflow.taskflow.common.exception.SelfDependencyException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.dto.DependencyResponse;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.graph.CycleDetectionService;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.project.entity.Project;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDependencyServiceTest {

    @Mock
    private TaskDependencyRepository dependencyRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private DependencyGraphBuilder graphBuilder;

    @Mock
    private CycleDetectionService cycleDetectionService;

    @Mock
    private GraphTraversalService traversalService;

    @Mock
    private com.taskflow.taskflow.dependency.readiness.DependencyReadinessService readinessService;

    @Mock
    private com.taskflow.taskflow.scheduling.service.SchedulingService schedulingService;

    @InjectMocks
    private TaskDependencyService dependencyService;

    private Project projectA;
    private Project projectB;
    private Task taskA;
    private Task taskB;
    private Task taskInProjectB;

    @BeforeEach
    void setUp() {
        projectA = new Project(UUID.randomUUID(), "Project A", "First workspace");
        projectB = new Project(UUID.randomUUID(), "Project B", "Second workspace");

        taskA = new Task(UUID.randomUUID(), projectA, "Task A", "Predecessor", TaskStatus.DONE, null, null, null, null);
        taskB = new Task(UUID.randomUUID(), projectA, "Task B", "Successor", TaskStatus.BACKLOG, null, null, null, null);
        taskInProjectB = new Task(UUID.randomUUID(), projectB, "Task in B", "Alien task", TaskStatus.BACKLOG, null, null, null, null);
    }

    @Test
    @DisplayName("Should create dependency when valid, acyclic, and within same project")
    void shouldCreateDependencySuccessfully() {
        CreateDependencyRequest request = new CreateDependencyRequest(taskA.getId(), taskB.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        when(dependencyRepository.existsByPredecessorIdAndSuccessorId(taskA.getId(), taskB.getId())).thenReturn(false);

        DependencyGraph projectGraph = new DependencyGraph(projectA.getId());
        when(graphBuilder.buildGraphForProject(projectA.getId())).thenReturn(projectGraph);
        when(cycleDetectionService.wouldCreateCycle(projectGraph, taskA.getId(), taskB.getId())).thenReturn(false);

        TaskDependency savedDependency = new TaskDependency(UUID.randomUUID(), taskA, taskB);
        when(dependencyRepository.saveAndFlush(any(TaskDependency.class))).thenReturn(savedDependency);

        DependencyResponse response = dependencyService.createDependency(request);

        assertThat(response).isNotNull();
        assertThat(response.predecessorTaskId()).isEqualTo(taskA.getId());
        assertThat(response.successorTaskId()).isEqualTo(taskB.getId());
        verify(dependencyRepository).saveAndFlush(any(TaskDependency.class));
        verify(readinessService).recalculateTaskAndDescendants(taskB.getId());
        verify(schedulingService).recalculateTaskAndDescendants(taskB.getId());
    }

    @Test
    @DisplayName("Should delete dependency and trigger readiness recalculation for successor")
    void shouldDeleteDependencySuccessfully() {
        when(dependencyRepository.existsByPredecessorIdAndSuccessorId(taskA.getId(), taskB.getId())).thenReturn(true);

        dependencyService.deleteDependency(taskA.getId(), taskB.getId());

        verify(dependencyRepository).deleteByPredecessorIdAndSuccessorId(taskA.getId(), taskB.getId());
        verify(dependencyRepository).flush();
        verify(readinessService).recalculateTaskAndDescendants(taskB.getId());
        verify(schedulingService).recalculateTaskAndDescendants(taskB.getId());
    }

    @Test
    @DisplayName("Should reject dependency when circular dependency is detected")
    void shouldRejectCircularDependency() {
        CreateDependencyRequest request = new CreateDependencyRequest(taskB.getId(), taskA.getId());

        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(dependencyRepository.existsByPredecessorIdAndSuccessorId(taskB.getId(), taskA.getId())).thenReturn(false);

        DependencyGraph projectGraph = new DependencyGraph(projectA.getId());
        when(graphBuilder.buildGraphForProject(projectA.getId())).thenReturn(projectGraph);
        when(cycleDetectionService.wouldCreateCycle(projectGraph, taskB.getId(), taskA.getId())).thenReturn(true);
        when(cycleDetectionService.getPotentialCyclePath(projectGraph, taskB.getId(), taskA.getId()))
                .thenReturn(Optional.of(List.of(taskB.getId(), taskA.getId(), taskB.getId())));

        assertThatThrownBy(() -> dependencyService.createDependency(request))
                .isInstanceOf(CycleDetectedException.class)
                .hasMessageContaining("Circular dependency detected");

        verify(dependencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject self-dependency")
    void shouldRejectSelfDependency() {
        CreateDependencyRequest request = new CreateDependencyRequest(taskA.getId(), taskA.getId());

        assertThatThrownBy(() -> dependencyService.createDependency(request))
                .isInstanceOf(SelfDependencyException.class)
                .hasMessageContaining("Self-dependency is forbidden");

        verify(dependencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject missing predecessor task")
    void shouldRejectMissingPredecessorTask() {
        UUID unknownId = UUID.randomUUID();
        CreateDependencyRequest request = new CreateDependencyRequest(unknownId, taskB.getId());

        when(taskRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dependencyService.createDependency(request))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(unknownId.toString());

        verify(dependencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject cross-project dependency (project isolation invariant)")
    void shouldRejectCrossProjectDependency() {
        CreateDependencyRequest request = new CreateDependencyRequest(taskA.getId(), taskInProjectB.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findById(taskInProjectB.getId())).thenReturn(Optional.of(taskInProjectB));

        assertThatThrownBy(() -> dependencyService.createDependency(request))
                .isInstanceOf(InvalidDependencyException.class)
                .hasMessageContaining("Cross-project dependencies are forbidden");

        verify(dependencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject duplicate dependency edge")
    void shouldRejectDuplicateDependency() {
        CreateDependencyRequest request = new CreateDependencyRequest(taskA.getId(), taskB.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        when(dependencyRepository.existsByPredecessorIdAndSuccessorId(taskA.getId(), taskB.getId())).thenReturn(true);

        assertThatThrownBy(() -> dependencyService.createDependency(request))
                .isInstanceOf(DuplicateDependencyException.class)
                .hasMessageContaining("already exists");

        verify(dependencyRepository, never()).save(any());
    }
}
