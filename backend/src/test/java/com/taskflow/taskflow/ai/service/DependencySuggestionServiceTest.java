package com.taskflow.taskflow.ai.service;

import com.taskflow.taskflow.ai.config.AiProperties;
import com.taskflow.taskflow.ai.dto.AcceptSuggestionRequest;
import com.taskflow.taskflow.ai.dto.DependencySuggestionResponse;
import com.taskflow.taskflow.ai.exception.AiDisabledException;
import com.taskflow.taskflow.ai.exception.AiMalformedOutputException;
import com.taskflow.taskflow.ai.exception.AiProviderException;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;
import com.taskflow.taskflow.ai.model.RawDependencySuggestion;
import com.taskflow.taskflow.ai.provider.DependencySuggestionProvider;
import com.taskflow.taskflow.common.exception.CycleDetectedException;
import com.taskflow.taskflow.common.exception.DuplicateDependencyException;
import com.taskflow.taskflow.common.exception.SelfDependencyException;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.dto.DependencyResponse;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
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

import java.time.Instant;
import java.time.LocalDate;
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
class DependencySuggestionServiceTest {

    @Mock
    private AiProperties aiProperties;

    @Mock
    private DependencySuggestionProvider provider;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskDependencyRepository dependencyRepository;

    @Mock
    private TaskDependencyService taskDependencyService;

    @Mock
    private DependencyGraphBuilder graphBuilder;

    @Mock
    private GraphTraversalService traversalService;

    @InjectMocks
    private DependencySuggestionService suggestionService;

    private Project project;
    private Task taskA;
    private Task taskB;
    private Task taskC;
    private DependencyGraph emptyGraph;

    @BeforeEach
    void setUp() {
        project = new Project("Test Project", "Description");
        taskA = new Task(project, "Task A", "Description A", TaskStatus.BACKLOG, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), 5);
        taskB = new Task(project, "Task B", "Description B", TaskStatus.BACKLOG, LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 10), 5);
        taskC = new Task(project, "Task C", "Description C", TaskStatus.BACKLOG, LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 15), 5);
        emptyGraph = new DependencyGraph();
    }

    private void enableAi() {
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getMaxSuggestions()).thenReturn(10);
    }

    @Test
    @DisplayName("Test 1: Valid AI suggestion - maps titles, confidence, and reason successfully")
    void test1_validAiSuggestion() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(emptyGraph);

        when(provider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), 0.91, "Task B requires Task A to complete first")
        )));

        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskA.getId(), 5);

        assertThat(response.targetTaskId()).isEqualTo(taskA.getId());
        assertThat(response.suggestionCount()).isEqualTo(1);
        assertThat(response.suggestions()).hasSize(1);
        var suggestion = response.suggestions().get(0);
        assertThat(suggestion.predecessor().taskId()).isEqualTo(taskA.getId());
        assertThat(suggestion.predecessor().title()).isEqualTo("Task A");
        assertThat(suggestion.successor().taskId()).isEqualTo(taskB.getId());
        assertThat(suggestion.successor().title()).isEqualTo("Task B");
        assertThat(suggestion.confidence()).isEqualTo(0.91);
        assertThat(suggestion.reason()).isEqualTo("Task B requires Task A to complete first");
    }

    @Test
    @DisplayName("Test 2: Unknown task ID - hallucinated task IDs are rejected")
    void test2_unknownTaskId() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(emptyGraph);

        UUID hallucinatedId = UUID.randomUUID();
        when(provider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(hallucinatedId, taskB.getId(), 0.85, "Hallucinated predecessor"),
                new RawDependencySuggestion(taskA.getId(), hallucinatedId, 0.85, "Hallucinated successor")
        )));

        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskA.getId(), null);

        assertThat(response.suggestionCount()).isZero();
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Test 3: Self-dependency - suggestions where predecessor == successor are rejected")
    void test3_selfDependency() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(emptyGraph);

        when(provider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(taskA.getId(), taskA.getId(), 0.99, "Task depends on itself")
        )));

        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskA.getId(), null);

        assertThat(response.suggestionCount()).isZero();
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Test 4: Duplicate dependency - suggestions that already exist in database are filtered")
    void test4_duplicateDependency() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));

        TaskDependency existingDep = new TaskDependency(taskA, taskB);
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of(existingDep));
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(emptyGraph);

        when(provider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), 0.95, "Already existing dependency")
        )));

        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskA.getId(), null);

        assertThat(response.suggestionCount()).isZero();
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Test 5: Cross-project suggestion - tasks from other projects are filtered")
    void test5_crossProjectSuggestion() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        // Project only contains taskA and taskB
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(emptyGraph);

        Project otherProject = new Project("Other Project", "Other Desc");
        Task otherTask = new Task(otherProject, "Other Task", "Other", TaskStatus.BACKLOG, null, null, null);

        when(provider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(taskA.getId(), otherTask.getId(), 0.88, "Cross-project dependency")
        )));

        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskA.getId(), null);

        assertThat(response.suggestionCount()).isZero();
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Test 6: Invalid confidence - confidence outside [0.0, 1.0] is rejected")
    void test6_invalidConfidence() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(emptyGraph);

        when(provider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), 2.4, "Confidence above 1.0"),
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), -0.5, "Negative confidence"),
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), null, "Null confidence")
        )));

        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskA.getId(), null);

        assertThat(response.suggestionCount()).isZero();
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Test 7: Missing reason - suggestions with null or blank reasons are rejected")
    void test7_missingReason() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(emptyGraph);

        when(provider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), 0.9, ""),
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), 0.9, "   "),
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), 0.9, null)
        )));

        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskA.getId(), null);

        assertThat(response.suggestionCount()).isZero();
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Test 8: Malformed provider output - throws controlled AiMalformedOutputException")
    void test8_malformedProviderOutput() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());

        when(provider.suggest(any())).thenThrow(new AiMalformedOutputException("Malformed JSON from provider"));

        assertThatThrownBy(() -> suggestionService.generateSuggestions(taskA.getId(), null))
                .isInstanceOf(AiMalformedOutputException.class)
                .hasMessageContaining("Malformed JSON");
    }

    @Test
    @DisplayName("Test 9: Provider timeout - throws controlled AiProviderException")
    void test9_providerTimeout() {
        enableAi();
        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());

        when(provider.suggest(any())).thenThrow(new AiProviderException("AI provider request timed out"));

        assertThatThrownBy(() -> suggestionService.generateSuggestions(taskA.getId(), null))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("Test 10: AI disabled - throws AiDisabledException when ai.enabled=false")
    void test10_aiDisabled() {
        when(aiProperties.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> suggestionService.generateSuggestions(taskA.getId(), null))
                .isInstanceOf(AiDisabledException.class)
                .hasMessageContaining("disabled");

        verify(provider, never()).suggest(any());
    }

    @Test
    @DisplayName("Test 11: Cycle suggestion - suggestion closing a cycle is filtered out during generation")
    void test11_cycleSuggestionFilteredDuringGeneration() {
        enableAi();
        when(taskRepository.findById(taskC.getId())).thenReturn(Optional.of(taskC));
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(taskA, taskB, taskC));
        when(dependencyRepository.findByProjectId(project.getId())).thenReturn(List.of());

        DependencyGraph graphWithChain = new DependencyGraph();
        graphWithChain.addEdge(taskA.getId(), taskB.getId());
        graphWithChain.addEdge(taskB.getId(), taskC.getId());

        when(graphBuilder.buildGraphForProject(project.getId())).thenReturn(graphWithChain);
        // Is task A reachable from task C? Yes, because A -> B -> C means C is reachable from A,
        // so adding C -> A would close a cycle! traversalService.isReachable(graph, successorId, predecessorId)
        when(traversalService.isReachable(graphWithChain, taskA.getId(), taskC.getId())).thenReturn(true);

        when(provider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(taskC.getId(), taskA.getId(), 0.9, "Suggesting C -> A closes cycle")
        )));

        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskC.getId(), null);

        assertThat(response.suggestionCount()).isZero();
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("Test 11b: Cycle suggestion on accept - deterministic engine rejects with CycleDetectedException")
    void test11b_cycleSuggestionRejectedOnAccept() {
        AcceptSuggestionRequest acceptRequest = new AcceptSuggestionRequest(taskC.getId(), taskA.getId());

        when(taskDependencyService.createDependency(new CreateDependencyRequest(taskC.getId(), taskA.getId())))
                .thenThrow(new CycleDetectedException("Cycle detected", List.of(taskA.getId(), taskB.getId(), taskC.getId(), taskA.getId())));

        assertThatThrownBy(() -> suggestionService.acceptSuggestion(acceptRequest))
                .isInstanceOf(CycleDetectedException.class)
                .hasMessageContaining("Cycle detected");
    }

    @Test
    @DisplayName("Test 12: Successful acceptance - delegates to deterministic dependency service")
    void test12_successfulAcceptance() {
        AcceptSuggestionRequest acceptRequest = new AcceptSuggestionRequest(taskA.getId(), taskB.getId());

        DependencyResponse expectedResponse = new DependencyResponse(
                UUID.randomUUID(),
                taskA.getId(),
                taskB.getId(),
                Instant.now()
        );

        when(taskDependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskB.getId())))
                .thenReturn(expectedResponse);

        DependencyResponse response = suggestionService.acceptSuggestion(acceptRequest);

        assertThat(response).isNotNull();
        assertThat(response.predecessorTaskId()).isEqualTo(taskA.getId());
        assertThat(response.successorTaskId()).isEqualTo(taskB.getId());
        verify(taskDependencyService).createDependency(new CreateDependencyRequest(taskA.getId(), taskB.getId()));
    }
}
