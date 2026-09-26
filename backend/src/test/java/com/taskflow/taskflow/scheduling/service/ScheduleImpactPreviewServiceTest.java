package com.taskflow.taskflow.scheduling.service;

import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.common.exception.ValidationException;
import com.taskflow.taskflow.dependency.graph.AffectedSubgraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.scheduling.dto.ScheduleImpactPreviewResponse;
import com.taskflow.taskflow.scheduling.dto.SchedulePreviewRequest;
import com.taskflow.taskflow.scheduling.dto.TaskScheduleImpactDto;
import com.taskflow.taskflow.scheduling.model.ImpactType;
import com.taskflow.taskflow.scheduling.model.ReasonType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleImpactPreviewServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private DependencyGraphBuilder graphBuilder;

    @Mock
    private GraphTraversalService traversalService;

    @Spy
    private ScheduleCalculationService calculationService = new ScheduleCalculationService();

    @InjectMocks
    private ScheduleImpactPreviewService previewService;

    private Project project;
    private Task taskA;
    private Task taskB;
    private Task taskC;
    private Task taskD;

    @BeforeEach
    void setUp() {
        project = new Project(UUID.randomUUID(), "Test Project", "Desc");

        taskA = new Task(UUID.randomUUID(), project, "Task A", "A", TaskStatus.BACKLOG, DependencyStatus.READY,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 3);
        taskB = new Task(UUID.randomUUID(), project, "Task B", "B", TaskStatus.BACKLOG, DependencyStatus.READY,
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 5), 2);
        taskC = new Task(UUID.randomUUID(), project, "Task C", "C", TaskStatus.BACKLOG, DependencyStatus.READY,
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 5), 2);
        taskD = new Task(UUID.randomUUID(), project, "Task D", "D", TaskStatus.BACKLOG, DependencyStatus.READY,
                LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 7), 2);
    }

    @Test
    @DisplayName("Root task with no descendants: only source task is returned and summary indicates 1 task")
    void shouldPreviewRootTaskWithNoDescendants() {
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA));

        AffectedSubgraph empty = new AffectedSubgraph(taskA.getId(), Collections.emptySet(), Collections.emptyList());
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(empty);

        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 5));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        assertThat(response.sourceTaskId()).isEqualTo(taskA.getId());
        assertThat(response.summary().affectedTaskCount()).isEqualTo(1);
        assertThat(response.summary().changedTaskCount()).isEqualTo(1);
        assertThat(response.summary().unchangedTaskCount()).isEqualTo(0);
        assertThat(response.summary().maximumDelayDays()).isEqualTo(4);

        TaskScheduleImpactDto rootImpact = response.tasks().get(0);
        assertThat(rootImpact.taskId()).isEqualTo(taskA.getId());
        assertThat(rootImpact.currentScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(rootImpact.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(rootImpact.currentScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(rootImpact.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(rootImpact.startShiftDays()).isEqualTo(4);
        assertThat(rootImpact.impactType()).isEqualTo(ImpactType.DELAYED);
        assertThat(rootImpact.reasonType()).isEqualTo(ReasonType.SOURCE_TASK_CHANGE);

        // Verify side-effect freedom: entity was not modified and repository was never called to save
        assertThat(taskA.getPlannedStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(taskA.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        verify(taskRepository, never()).save(any());
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Linear chain: A -> B -> C updates all downstream tasks in topological order")
    void shouldPreviewLinearChainPropagation() {
        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addNode(taskC.getId());
        graph.addEdge(taskA.getId(), taskB.getId());
        graph.addEdge(taskB.getId(), taskC.getId());

        // In linear chain A -> B -> C, C currently starts after B finishes (June 6 - 7)
        taskC.setScheduledDates(LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 7));
        taskC.setPlannedSchedule(LocalDate.of(2026, 6, 6), 2);

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB, taskC));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(),
                Set.of(taskB.getId(), taskC.getId()),
                List.of(taskB.getId(), taskC.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        // Propose A +3 days: June 4
        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 4));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        assertThat(response.summary().affectedTaskCount()).isEqualTo(3);
        assertThat(response.summary().changedTaskCount()).isEqualTo(3);
        assertThat(response.summary().maximumDelayDays()).isEqualTo(3);

        TaskScheduleImpactDto impactA = response.tasks().get(0);
        TaskScheduleImpactDto impactB = response.tasks().get(1);
        TaskScheduleImpactDto impactC = response.tasks().get(2);

        // A shifted June 1 -> June 4, due June 3 -> June 6
        assertThat(impactA.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(impactA.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(impactA.startShiftDays()).isEqualTo(3);
        assertThat(impactA.impactType()).isEqualTo(ImpactType.DELAYED);

        // B constrained by A (due June 6) -> starts June 7, due June 8 (+3 days)
        assertThat(impactB.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(impactB.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(impactB.startShiftDays()).isEqualTo(3);
        assertThat(impactB.impactType()).isEqualTo(ImpactType.CONSTRAINT_DRIVEN);
        assertThat(impactB.reasonType()).isEqualTo(ReasonType.DEPENDENCY_CONSTRAINT);
        assertThat(impactB.constraintSourceTaskIds()).containsExactly(taskA.getId());

        // C constrained by B (due June 8) -> starts June 9, due June 10 (+5 days from initial June 4)
        assertThat(impactC.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(impactC.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(impactC.impactType()).isEqualTo(ImpactType.CONSTRAINT_DRIVEN);
        assertThat(impactC.constraintSourceTaskIds()).containsExactly(taskB.getId());
    }

    @Test
    @DisplayName("Branching graph: A -> B, A -> C recalculates both branches independently")
    void shouldPreviewBranchingGraph() {
        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addNode(taskC.getId());
        graph.addEdge(taskA.getId(), taskB.getId());
        graph.addEdge(taskA.getId(), taskC.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB, taskC));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(),
                Set.of(taskB.getId(), taskC.getId()),
                List.of(taskB.getId(), taskC.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 4));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        assertThat(response.summary().affectedTaskCount()).isEqualTo(3);
        TaskScheduleImpactDto impactB = response.tasks().get(1);
        TaskScheduleImpactDto impactC = response.tasks().get(2);

        assertThat(impactB.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(impactC.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(impactB.constraintSourceTaskIds()).containsExactly(taskA.getId());
        assertThat(impactC.constraintSourceTaskIds()).containsExactly(taskA.getId());
    }

    @Test
    @DisplayName("Converging paths (A -> B -> D, A -> C -> D): delay does NOT compound on D (critical test)")
    void shouldNotCompoundDelayOnConvergingPaths() {
        // Graph structure:
        // A (June 1 - 3)
        // B (June 4 - 5) depends on A
        // C (June 4 - 5) depends on A
        // D (June 6 - 7) depends on both B and C
        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addNode(taskC.getId());
        graph.addNode(taskD.getId());
        graph.addEdge(taskA.getId(), taskB.getId());
        graph.addEdge(taskA.getId(), taskC.getId());
        graph.addEdge(taskB.getId(), taskD.getId());
        graph.addEdge(taskC.getId(), taskD.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB, taskC, taskD));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(),
                Set.of(taskB.getId(), taskC.getId(), taskD.getId()),
                List.of(taskB.getId(), taskC.getId(), taskD.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        // Propose A +3 days: plannedStart June 4 (current is June 1)
        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 4));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        assertThat(response.summary().affectedTaskCount()).isEqualTo(4);
        assertThat(response.summary().changedTaskCount()).isEqualTo(4);
        assertThat(response.summary().maximumDelayDays()).isEqualTo(3);

        TaskScheduleImpactDto impactA = response.tasks().get(0);
        TaskScheduleImpactDto impactB = response.tasks().get(1);
        TaskScheduleImpactDto impactC = response.tasks().get(2);
        TaskScheduleImpactDto impactD = response.tasks().get(3);

        // A moves +3 days: June 4 - 6
        assertThat(impactA.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(impactA.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(impactA.startShiftDays()).isEqualTo(3);

        // B moves +3 days: June 7 - 8
        assertThat(impactB.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(impactB.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(impactB.startShiftDays()).isEqualTo(3);

        // C moves +3 days: June 7 - 8
        assertThat(impactC.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(impactC.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(impactC.startShiftDays()).isEqualTo(3);

        // CRITICAL CHECK: D must move +3 days (June 9 - 10), NOT +6 days!
        assertThat(impactD.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(impactD.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(impactD.startShiftDays()).isEqualTo(3);
        assertThat(impactD.startShiftDays()).isNotEqualTo(6);

        // Verify binding predecessors: both B and C finish on June 8, imposing constraint June 9
        assertThat(impactD.impactType()).isEqualTo(ImpactType.CONSTRAINT_DRIVEN);
        assertThat(impactD.reasonType()).isEqualTo(ReasonType.DEPENDENCY_CONSTRAINT);
        assertThat(impactD.constraintSourceTaskIds()).containsExactlyInAnyOrder(taskB.getId(), taskC.getId());
        assertThat(impactD.constraintDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(impactD.reason()).contains("Non-compounding scheduling applies");
    }

    @Test
    @DisplayName("Multiple predecessors: latest constraint wins and binding predecessor is accurately identified")
    void shouldIdentifySingleBindingPredecessorAmongMultiple() {
        // A due = June 10, B due = June 15, C due = June 12
        // All precede D
        taskA.setScheduledDates(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 10));
        taskB.setScheduledDates(LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 15));
        taskC.setScheduledDates(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 12));
        taskD.setScheduledDates(LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 17));

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addNode(taskC.getId());
        graph.addNode(taskD.getId());
        graph.addEdge(taskA.getId(), taskD.getId());
        graph.addEdge(taskB.getId(), taskD.getId());
        graph.addEdge(taskC.getId(), taskD.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB, taskC, taskD));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(), Set.of(taskD.getId()), List.of(taskD.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        // Move A +2 days: plannedStart June 10, due June 12
        // Even with A moving to due June 12, B's due June 15 remains the latest constraint!
        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 10));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        TaskScheduleImpactDto impactD = response.tasks().get(1);
        assertThat(impactD.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(impactD.startShiftDays()).isEqualTo(0);
        assertThat(impactD.constraintDate()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(impactD.constraintSourceTaskIds()).containsExactly(taskB.getId());
    }

    @Test
    @DisplayName("Baseline test: planned schedule later than dependency constraint remains unchanged without artificial delay")
    void shouldNotArtificiallyDelayTaskWhenPlannedDateIsLaterThanConstraint() {
        // B planned start = June 20, constraint from A = June 15
        taskB.setPlannedSchedule(LocalDate.of(2026, 6, 20), 2);
        taskB.setScheduledDates(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 21));

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addEdge(taskA.getId(), taskB.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(), Set.of(taskB.getId()), List.of(taskB.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        // Move A so it ends on June 14 (constraint June 15)
        // plannedStart = June 12, duration 3 -> due June 14
        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 12));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        TaskScheduleImpactDto impactB = response.tasks().get(1);
        assertThat(impactB.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(impactB.startShiftDays()).isEqualTo(0);
        assertThat(impactB.impactType()).isEqualTo(ImpactType.UNCHANGED);
        assertThat(impactB.reasonType()).isEqualTo(ReasonType.PLANNED_DATE_DOMINANT);
        assertThat(impactB.reason()).contains("already later than the dependency constraint");
    }

    @Test
    @DisplayName("Upstream moved earlier: downstream task returns toward planned baseline schedule")
    void shouldPreviewEarlierChangeReturningTowardBaseline() {
        // Task A was pushed to June 10 - June 12
        taskA.setPlannedSchedule(LocalDate.of(2026, 6, 10), 3);
        taskA.setScheduledDates(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 12));

        // Task B has planned start June 1, but was constrained to June 13 - June 14
        taskB.setPlannedSchedule(LocalDate.of(2026, 6, 1), 2);
        taskB.setScheduledDates(LocalDate.of(2026, 6, 13), LocalDate.of(2026, 6, 14));

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addEdge(taskA.getId(), taskB.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(), Set.of(taskB.getId()), List.of(taskB.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        // Move A earlier to June 1 - June 3
        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 1));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        TaskScheduleImpactDto impactB = response.tasks().get(1);
        // A now finishes June 3, constraint is June 4.
        // B planned start is June 1, so max(June 1, June 4) = June 4.
        // B moves from June 13 to June 4 (-9 days shift!)
        assertThat(impactB.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(impactB.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(impactB.startShiftDays()).isEqualTo(-9);
        assertThat(impactB.impactType()).isEqualTo(ImpactType.MOVED_EARLIER);
    }

    @Test
    @DisplayName("Same proposed date: preview returns changedTaskCount = 0 and UNCHANGED impact")
    void shouldReturnZeroChangesWhenProposedDateIsSame() {
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA));

        AffectedSubgraph empty = new AffectedSubgraph(taskA.getId(), Collections.emptySet(), Collections.emptyList());
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(empty);

        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 1));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        assertThat(response.summary().changedTaskCount()).isEqualTo(0);
        assertThat(response.summary().unchangedTaskCount()).isEqualTo(1);
        assertThat(response.summary().maximumDelayDays()).isEqualTo(0);
        assertThat(response.tasks().get(0).impactType()).isEqualTo(ImpactType.UNCHANGED);
    }

    @Test
    @DisplayName("Idempotency: Repeated preview calls produce identical results without state drift")
    void shouldBeIdempotentOnRepeatedCalls() {
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA));

        AffectedSubgraph empty = new AffectedSubgraph(taskA.getId(), Collections.emptySet(), Collections.emptyList());
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(empty);

        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 10));
        ScheduleImpactPreviewResponse preview1 = previewService.calculatePreview(taskA.getId(), request);
        ScheduleImpactPreviewResponse preview2 = previewService.calculatePreview(taskA.getId(), request);

        assertThat(preview1.summary()).isEqualTo(preview2.summary());
        assertThat(preview1.tasks().get(0).proposedScheduledStart())
                .isEqualTo(preview2.tasks().get(0).proposedScheduledStart());
    }

    @Test
    @DisplayName("Unrelated graph: preview of A -> B -> C never contains components from X -> Y -> Z")
    void shouldNotAffectUnrelatedGraphs() {
        Task taskX = new Task(UUID.randomUUID(), project, "Task X", "X", TaskStatus.BACKLOG, DependencyStatus.READY,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 2);
        Task taskY = new Task(UUID.randomUUID(), project, "Task Y", "Y", TaskStatus.BACKLOG, DependencyStatus.READY,
                LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 4), 2);

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addNode(taskX.getId());
        graph.addNode(taskY.getId());
        graph.addEdge(taskA.getId(), taskB.getId());
        graph.addEdge(taskX.getId(), taskY.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB, taskX, taskY));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(), Set.of(taskB.getId()), List.of(taskB.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 4));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        // Preview should contain ONLY A and B, NEVER X or Y
        assertThat(response.tasks())
                .extracting(TaskScheduleImpactDto::taskId)
                .containsExactly(taskA.getId(), taskB.getId())
                .doesNotContain(taskX.getId(), taskY.getId());
    }

    @Test
    @DisplayName("Multi-level chain: A -> B -> C -> D calculates each stage from immediately updated predecessor")
    void shouldPropagateMultiLevelChainTopologically() {
        taskA.setPlannedSchedule(LocalDate.of(2026, 6, 1), 2);
        taskA.setScheduledDates(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

        taskB.setPlannedSchedule(LocalDate.of(2026, 6, 3), 2);
        taskB.setScheduledDates(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 4));

        taskC.setPlannedSchedule(LocalDate.of(2026, 6, 5), 2);
        taskC.setScheduledDates(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 6));

        taskD.setPlannedSchedule(LocalDate.of(2026, 6, 7), 2);
        taskD.setScheduledDates(LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 8));

        DependencyGraph graph = new DependencyGraph(project.getId());
        graph.addNode(taskA.getId());
        graph.addNode(taskB.getId());
        graph.addNode(taskC.getId());
        graph.addNode(taskD.getId());
        graph.addEdge(taskA.getId(), taskB.getId());
        graph.addEdge(taskB.getId(), taskC.getId());
        graph.addEdge(taskC.getId(), taskD.getId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graph);
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB, taskC, taskD));

        AffectedSubgraph affected = new AffectedSubgraph(taskA.getId(),
                Set.of(taskB.getId(), taskC.getId(), taskD.getId()),
                List.of(taskB.getId(), taskC.getId(), taskD.getId()));
        when(traversalService.getAffectedSubgraph(graph, taskA.getId())).thenReturn(affected);

        // Move A +5 days: June 6
        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 6));
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskA.getId(), request);

        assertThat(response.summary().affectedTaskCount()).isEqualTo(4);

        TaskScheduleImpactDto impA = response.tasks().get(0);
        TaskScheduleImpactDto impB = response.tasks().get(1);
        TaskScheduleImpactDto impC = response.tasks().get(2);
        TaskScheduleImpactDto impD = response.tasks().get(3);

        // A: June 6 -> June 7
        assertThat(impA.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(impA.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 7));

        // B: June 8 -> June 9
        assertThat(impB.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(impB.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(impB.constraintSourceTaskIds()).containsExactly(taskA.getId());

        // C: June 10 -> June 11
        assertThat(impC.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(impC.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(impC.constraintSourceTaskIds()).containsExactly(taskB.getId());

        // D: June 12 -> June 13
        assertThat(impD.proposedScheduledStart()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(impD.proposedScheduledDue()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(impD.constraintSourceTaskIds()).containsExactly(taskC.getId());
    }

    @Test
    @DisplayName("Invalid task ID throws TaskNotFoundException")
    void shouldThrowTaskNotFoundExceptionWhenTaskDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(taskRepository.findById(unknownId)).thenReturn(Optional.empty());

        SchedulePreviewRequest request = new SchedulePreviewRequest(LocalDate.of(2026, 6, 1));
        assertThatThrownBy(() -> previewService.calculatePreview(unknownId, request))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    @DisplayName("Null proposed date throws ValidationException")
    void shouldThrowValidationExceptionWhenProposedDateIsNull() {
        SchedulePreviewRequest request = new SchedulePreviewRequest(null);
        assertThatThrownBy(() -> previewService.calculatePreview(taskA.getId(), request))
                .isInstanceOf(ValidationException.class);
    }
}
