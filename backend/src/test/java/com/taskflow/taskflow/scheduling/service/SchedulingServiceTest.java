package com.taskflow.taskflow.scheduling.service;

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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

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

    @Spy
    private ScheduleCalculationService calculationService = new ScheduleCalculationService();

    @InjectMocks
    private SchedulingService schedulingService;

    private Project project;
    private Task taskA;
    private Task taskB;

    @BeforeEach
    void setUp() {
        project = new Project(UUID.randomUUID(), "Test Project", "Desc");
        taskA = new Task(UUID.randomUUID(), project, "Task A", "A", TaskStatus.IN_PROGRESS, DependencyStatus.READY,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 3);
        taskB = new Task(UUID.randomUUID(), project, "Task B", "B", TaskStatus.BACKLOG, DependencyStatus.BLOCKED,
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 6), 3);
    }

    @Test
    @DisplayName("Should shift downstream successor when upstream task schedule is moved later")
    void shouldShiftSuccessorWhenUpstreamTaskMovesLater() {
        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addEdge(taskA.getId(), taskB.getId());

        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(), Set.of(taskB.getId()), List.of(taskB.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        // Move task A by +3 days: planned start June 4
        LocalDate newStartA = LocalDate.of(2026, 6, 4);
        schedulingService.updateTaskSchedule(taskA, newStartA, 3);

        // A is now June 4 - June 6
        assertThat(taskA.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(taskA.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 6));

        // B must be shifted to June 7 - June 9
        assertThat(taskB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(taskB.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 9));

        verify(taskRepository).saveAll(any());
    }

    @Test
    @DisplayName("Should return empty list and not save when no descendants are affected")
    void shouldNotSaveWhenNoAffectedTasks() {
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));

        DependencyGraph graph = new DependencyGraph(project.getId());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);

        AffectedSubgraph empty = new AffectedSubgraph(taskA.getId(), Collections.emptySet(), Collections.emptyList());
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(empty);

        List<Task> changed = schedulingService.recalculateAffectedDescendants(taskA.getId());

        assertThat(changed).isEmpty();
        verify(taskRepository, never()).saveAll(any());
    }
}
