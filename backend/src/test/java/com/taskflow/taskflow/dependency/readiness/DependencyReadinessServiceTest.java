package com.taskflow.taskflow.dependency.readiness;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.dependency.graph.AffectedSubgraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.dependency.graph.TopologicalSortService;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyReadinessServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DependencyGraphBuilder graphBuilder;

    @Mock
    private GraphTraversalService traversalService;

    @Mock
    private TopologicalSortService topologicalSortService;

    @InjectMocks
    private DependencyReadinessService readinessService;

    private Project project;
    private Task rootTask;
    private Task childTask;

    @BeforeEach
    void setUp() {
        project = new Project(UUID.randomUUID(), "Test Project", "Workspace");
        rootTask = new Task(UUID.randomUUID(), project, "Root", "Predecessor", TaskStatus.DONE, DependencyStatus.READY, null, null, null);
        childTask = new Task(UUID.randomUUID(), project, "Child", "Successor", TaskStatus.BACKLOG, DependencyStatus.BLOCKED, null, null, null);
    }

    @Test
    @DisplayName("Should return empty list when no downstream tasks are affected")
    void shouldReturnEmptyWhenNoAffectedTasks() {
        when(taskRepository.findById(rootTask.getId())).thenReturn(Optional.of(rootTask));
        DependencyGraph graph = new DependencyGraph(project.getId());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);

        AffectedSubgraph emptySubgraph = new AffectedSubgraph(rootTask.getId(), Collections.emptySet(), Collections.emptyList());
        when(traversalService.getAffectedSubgraph(graph, rootTask.getId())).thenReturn(emptySubgraph);

        List<Task> changed = readinessService.recalculateAffectedDescendants(rootTask.getId());

        assertThat(changed).isEmpty();
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should update affected child task when predecessor is completed")
    void shouldUpdateChildTaskWhenPredecessorDone() {
        when(taskRepository.findById(rootTask.getId())).thenReturn(Optional.of(rootTask));

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(rootTask.getId());
        graph.addNode(childTask.getId());
        graph.addEdge(rootTask.getId(), childTask.getId());

        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);

        AffectedSubgraph affected = new AffectedSubgraph(rootTask.getId(), Set.of(childTask.getId()), List.of(childTask.getId()));
        when(traversalService.getAffectedSubgraph(graph, rootTask.getId())).thenReturn(affected);

        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(rootTask, childTask));

        List<Task> changed = readinessService.recalculateAffectedDescendants(rootTask.getId());

        assertThat(changed).hasSize(1);
        assertThat(childTask.getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        verify(taskRepository).saveAll(changed);
    }

    @Test
    @DisplayName("Should throw TaskNotFoundException when recalculating for unknown task")
    void shouldThrowTaskNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(taskRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> readinessService.recalculateForTask(unknownId))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("Should throw ProjectNotFoundException when recalculating for unknown project")
    void shouldThrowProjectNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(projectRepository.existsById(unknownId)).thenReturn(false);

        assertThatThrownBy(() -> readinessService.recalculateForProject(unknownId))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}
