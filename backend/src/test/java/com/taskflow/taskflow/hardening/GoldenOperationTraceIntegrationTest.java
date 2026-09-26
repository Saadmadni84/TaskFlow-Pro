package com.taskflow.taskflow.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoldenOperationTraceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        project = projectRepository.saveAndFlush(
                new Project("Operation Trace Project", "Trace validation")
        );
    }

    @Test
    @DisplayName("Golden Operation Trace: Diamond graph, readiness, no-compounding schedule propagation, cycle rejection, and correlation IDs")
    void executeGoldenOperationTrace() throws Exception {
        String correlationId = "trace-test-" + UUID.randomUUID();

        // 1. Create 4 tasks: A, B, C, D
        UUID taskA = createTask("Task A", 5, LocalDate.of(2026, 6, 1), correlationId);
        UUID taskB = createTask("Task B", 3, LocalDate.of(2026, 6, 1), correlationId);
        UUID taskC = createTask("Task C", 4, LocalDate.of(2026, 6, 1), correlationId);
        UUID taskD = createTask("Task D", 2, LocalDate.of(2026, 6, 1), correlationId);

        // 2. Create dependencies: A -> B, A -> C, B -> D, C -> D
        createDependency(taskA, taskB, correlationId);
        createDependency(taskA, taskC, correlationId);
        createDependency(taskB, taskD, correlationId);
        createDependency(taskC, taskD, correlationId);

        // Verify initial readiness: A is READY, B, C, D are BLOCKED
        mockMvc.perform(get("/api/tasks/" + taskA).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", correlationId))
                .andExpect(jsonPath("$.dependencyStatus").value("READY"));

        mockMvc.perform(get("/api/tasks/" + taskB).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("BLOCKED"));

        mockMvc.perform(get("/api/tasks/" + taskD).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("BLOCKED"));

        // 3. Mark A as DONE -> B and C become READY, D remains BLOCKED
        updateTaskStatus(taskA, TaskStatus.DONE, correlationId);

        mockMvc.perform(get("/api/tasks/" + taskB).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("READY"));
        mockMvc.perform(get("/api/tasks/" + taskC).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("READY"));
        mockMvc.perform(get("/api/tasks/" + taskD).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("BLOCKED"));

        // 4. Mark B as DONE -> D remains BLOCKED because C is still not DONE
        updateTaskStatus(taskB, TaskStatus.DONE, correlationId);
        mockMvc.perform(get("/api/tasks/" + taskD).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("BLOCKED"));

        // 5. Mark C as DONE -> D unlocks and becomes READY
        updateTaskStatus(taskC, TaskStatus.DONE, correlationId);
        mockMvc.perform(get("/api/tasks/" + taskD).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("READY"));

        // 6. Test Schedule Shift: move A by +3 days (from 2026-06-01 to 2026-06-04)
        mockMvc.perform(put("/api/tasks/" + taskA)
                        .header("X-Request-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTaskRequest(
                                "Task A",
                                "Updated schedule",
                                TaskStatus.DONE,
                                LocalDate.of(2026, 6, 4),
                                null,
                                5
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", correlationId));

        // In diamond graph:
        // A: 2026-06-04 to 2026-06-08 (5 days)
        // B: 2026-06-09 to 2026-06-11 (3 days)
        // C: 2026-06-09 to 2026-06-12 (4 days)
        // D: starts max(due(B), due(C)) + 1 = 2026-06-13
        // D shifted by exactly +3 days from original (2026-06-10 -> 2026-06-13), NEVER compounded to +6 days!
        mockMvc.perform(get("/api/tasks/" + taskD).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledStartDate").value("2026-06-13"));

        // 7. Move A back to IN_PROGRESS -> B, C, D all become BLOCKED
        updateTaskStatus(taskA, TaskStatus.IN_PROGRESS, correlationId);
        mockMvc.perform(get("/api/tasks/" + taskB).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("BLOCKED"));
        mockMvc.perform(get("/api/tasks/" + taskD).header("X-Request-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyStatus").value("BLOCKED"));

        // 8. Attempt to create a cycle: D -> A
        // Must be rejected with 400 or 409, code containing CYCLE, returning the correlation ID
        mockMvc.perform(post("/api/dependencies")
                        .header("X-Request-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDependencyRequest(taskD, taskA))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Request-Id", correlationId))
                .andExpect(jsonPath("$.code").value("CYCLE_DETECTED"))
                .andExpect(jsonPath("$.requestId").value(correlationId));

        // 9. Verify Actuator Health responds
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private UUID createTask(String title, int duration, LocalDate plannedStart, String correlationId) throws Exception {
        CreateTaskRequest request = new CreateTaskRequest(
                project.getId(),
                title,
                "Description for " + title,
                TaskStatus.BACKLOG,
                plannedStart,
                null,
                duration
        );

        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .header("X-Request-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", correlationId))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());
    }

    private void createDependency(UUID pred, UUID succ, String correlationId) throws Exception {
        mockMvc.perform(post("/api/dependencies")
                        .header("X-Request-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDependencyRequest(pred, succ))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", correlationId));
    }

    private void updateTaskStatus(UUID taskId, TaskStatus status, String correlationId) throws Exception {
        Task task = taskRepository.findById(taskId).orElseThrow();
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("X-Request-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTaskRequest(
                                task.getTitle(),
                                task.getDescription(),
                                status,
                                task.getPlannedStartDate(),
                                null,
                                task.getDurationDays()
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", correlationId));
    }
}
