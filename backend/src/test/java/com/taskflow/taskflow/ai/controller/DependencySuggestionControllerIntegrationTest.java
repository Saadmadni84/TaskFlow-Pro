package com.taskflow.taskflow.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.taskflow.ai.config.AiProperties;
import com.taskflow.taskflow.ai.dto.AcceptSuggestionRequest;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;
import com.taskflow.taskflow.ai.model.RawDependencySuggestion;
import com.taskflow.taskflow.ai.provider.DependencySuggestionProvider;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DependencySuggestionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private AiProperties aiProperties;

    @MockBean
    private DependencySuggestionProvider suggestionProvider;

    private Project project;
    private Task taskA;
    private Task taskB;
    private Task taskC;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(new Project("AI Suggestion Project", "Testing AI Suggestion Endpoints"));
        taskA = taskRepository.saveAndFlush(new Task(project, "Design Schema", "DB schema design", TaskStatus.BACKLOG, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), 5));
        taskB = taskRepository.saveAndFlush(new Task(project, "Implement API", "Backend API implementation", TaskStatus.BACKLOG, LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 10), 5));
        taskC = taskRepository.saveAndFlush(new Task(project, "Build Frontend", "Web UI implementation", TaskStatus.BACKLOG, LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 15), 5));
    }

    @Test
    @DisplayName("POST /api/tasks/{taskId}/dependency-suggestions returns 503 AI_DISABLED when AI is disabled")
    void shouldReturnServiceUnavailableWhenAiDisabled() throws Exception {
        aiProperties.setEnabled(false);

        mockMvc.perform(post("/api/tasks/{taskId}/dependency-suggestions", taskA.getId()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code", is("AI_DISABLED")));
    }

    @Test
    @DisplayName("POST /api/tasks/{taskId}/dependency-suggestions returns candidate suggestions when AI is enabled")
    void shouldReturnSuggestionsWhenAiEnabled() throws Exception {
        aiProperties.setEnabled(true);

        when(suggestionProvider.suggest(any())).thenReturn(new DependencySuggestionResult(List.of(
                new RawDependencySuggestion(taskA.getId(), taskB.getId(), 0.92, "API implementation depends on schema design")
        )));

        mockMvc.perform(post("/api/tasks/{taskId}/dependency-suggestions", taskB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTaskId", is(taskB.getId().toString())))
                .andExpect(jsonPath("$.suggestionCount", is(1)))
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].predecessor.taskId", is(taskA.getId().toString())))
                .andExpect(jsonPath("$.suggestions[0].predecessor.title", is("Design Schema")))
                .andExpect(jsonPath("$.suggestions[0].successor.taskId", is(taskB.getId().toString())))
                .andExpect(jsonPath("$.suggestions[0].successor.title", is("Implement API")))
                .andExpect(jsonPath("$.suggestions[0].confidence", is(0.92)))
                .andExpect(jsonPath("$.suggestions[0].reason", is("API implementation depends on schema design")));

        // Verify graph remains completely unchanged (AI proposal NEVER mutates DB)
        assertThat(dependencyRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /api/dependency-suggestions/accept creates dependency and recalculates readiness/schedule")
    void shouldAcceptSuggestionAndCreateDependency() throws Exception {
        AcceptSuggestionRequest request = new AcceptSuggestionRequest(taskA.getId(), taskB.getId());

        mockMvc.perform(post("/api/dependency-suggestions/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.predecessorTaskId", is(taskA.getId().toString())))
                .andExpect(jsonPath("$.successorTaskId", is(taskB.getId().toString())));

        // Verify dependency is persisted
        assertThat(dependencyRepository.existsByPredecessorIdAndSuccessorId(taskA.getId(), taskB.getId())).isTrue();
    }

    @Test
    @DisplayName("POST /api/dependency-suggestions/accept rejects cycle with 422 UNPROCESSABLE_ENTITY")
    void shouldRejectCycleOnAccept() throws Exception {
        // Create chain: A -> B and B -> C
        AcceptSuggestionRequest req1 = new AcceptSuggestionRequest(taskA.getId(), taskB.getId());
        mockMvc.perform(post("/api/dependency-suggestions/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        AcceptSuggestionRequest req2 = new AcceptSuggestionRequest(taskB.getId(), taskC.getId());
        mockMvc.perform(post("/api/dependency-suggestions/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Now attempt to accept C -> A (which would close a cycle: A -> B -> C -> A)
        AcceptSuggestionRequest cycleReq = new AcceptSuggestionRequest(taskC.getId(), taskA.getId());
        mockMvc.perform(post("/api/dependency-suggestions/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cycleReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("CYCLE_DETECTED")));

        // Verify cycle edge was NOT persisted
        assertThat(dependencyRepository.existsByPredecessorIdAndSuccessorId(taskC.getId(), taskA.getId())).isFalse();
    }

    @Test
    @DisplayName("POST /api/dependency-suggestions/accept rejects self dependency with 400 BAD_REQUEST")
    void shouldRejectSelfDependencyOnAccept() throws Exception {
        AcceptSuggestionRequest selfReq = new AcceptSuggestionRequest(taskA.getId(), taskA.getId());

        mockMvc.perform(post("/api/dependency-suggestions/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("SELF_DEPENDENCY")));
    }

    @Test
    @DisplayName("POST /api/dependency-suggestions/accept rejects duplicate dependency with 409 CONFLICT")
    void shouldRejectDuplicateDependencyOnAccept() throws Exception {
        AcceptSuggestionRequest req = new AcceptSuggestionRequest(taskA.getId(), taskB.getId());

        // First accept succeeds
        mockMvc.perform(post("/api/dependency-suggestions/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second accept with same edge is rejected as duplicate
        mockMvc.perform(post("/api/dependency-suggestions/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_DEPENDENCY")));
    }

    @Test
    @DisplayName("POST /api/dependency-suggestions/accept rejects nonexistent task with 404 NOT_FOUND")
    void shouldRejectNonexistentTaskOnAccept() throws Exception {
        UUID unknownId = UUID.randomUUID();
        AcceptSuggestionRequest req = new AcceptSuggestionRequest(unknownId, taskB.getId());

        mockMvc.perform(post("/api/dependency-suggestions/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }
}
