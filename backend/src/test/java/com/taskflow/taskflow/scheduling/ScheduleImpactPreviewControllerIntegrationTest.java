package com.taskflow.taskflow.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.scheduling.dto.ScheduleImpactPreviewResponse;
import com.taskflow.taskflow.scheduling.dto.SchedulePreviewRequest;
import com.taskflow.taskflow.scheduling.dto.TaskScheduleImpactDto;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleImpactPreviewControllerIntegrationTest {

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
    private TaskDependencyService dependencyService;

    private Project project;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(
                new Project("Impact Preview Project", "Testing side-effect-free preview endpoint")
        );
    }

    private Task createTask(String title, LocalDate plannedStart, int durationDays) {
        LocalDate due = plannedStart != null ? plannedStart.plusDays(durationDays - 1) : null;
        Task task = new Task(project, title, "Desc", TaskStatus.BACKLOG, plannedStart, due, durationDays);
        return taskRepository.saveAndFlush(task);
    }

    private void link(Task predecessor, Task successor) {
        dependencyService.createDependency(new CreateDependencyRequest(predecessor.getId(), successor.getId()));
    }

    @Test
    @DisplayName("Side-effect freedom: Preview predicts schedule shifts without mutating database or incrementing version")
    void shouldPreviewWithoutMutatingDatabaseState() throws Exception {
        // A (June 1 - 3), B (June 4 - 5)
        Task taskA = createTask("Task A", LocalDate.of(2026, 6, 1), 3);
        Task taskB = createTask("Task B", LocalDate.of(2026, 6, 4), 2);
        link(taskA, taskB);

        // Capture initial database state
        Task initialA = taskRepository.findById(taskA.getId()).orElseThrow();
        Task initialB = taskRepository.findById(taskB.getId()).orElseThrow();
        Long versionA = initialA.getVersion();
        Long versionB = initialB.getVersion();
        Instant updatedAtA = initialA.getUpdatedAt();
        Instant updatedAtB = initialB.getUpdatedAt();

        // Call preview for task A moving +3 days to June 4
        SchedulePreviewRequest previewReq = new SchedulePreviewRequest(LocalDate.of(2026, 6, 4));
        MvcResult result = mockMvc.perform(post("/api/tasks/{taskId}/schedule/preview", taskA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceTaskId").value(taskA.getId().toString()))
                .andExpect(jsonPath("$.summary.affectedTaskCount").value(2))
                .andExpect(jsonPath("$.summary.changedTaskCount").value(2))
                .andExpect(jsonPath("$.summary.maximumDelayDays").value(3))
                .andExpect(jsonPath("$.tasks[0].proposedScheduledStart").value("2026-06-04"))
                .andExpect(jsonPath("$.tasks[0].proposedScheduledDue").value("2026-06-06"))
                .andExpect(jsonPath("$.tasks[0].shiftDays").value(3))
                .andExpect(jsonPath("$.tasks[1].proposedScheduledStart").value("2026-06-07"))
                .andExpect(jsonPath("$.tasks[1].proposedScheduledDue").value("2026-06-08"))
                .andExpect(jsonPath("$.tasks[1].shiftDays").value(3))
                .andReturn();

        // CRITICAL CHECK: Verify GET /api/tasks/{id} shows database was NOT modified
        mockMvc.perform(get("/api/tasks/{id}", taskA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledStartDate").value("2026-06-01"))
                .andExpect(jsonPath("$.scheduledDueDate").value("2026-06-03"))
                .andExpect(jsonPath("$.version").value(versionA.intValue()));

        mockMvc.perform(get("/api/tasks/{id}", taskB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledStartDate").value("2026-06-04"))
                .andExpect(jsonPath("$.scheduledDueDate").value("2026-06-05"))
                .andExpect(jsonPath("$.version").value(versionB.intValue()));

        // Check entities directly in repository
        Task persistedA = taskRepository.findById(taskA.getId()).orElseThrow();
        Task persistedB = taskRepository.findById(taskB.getId()).orElseThrow();

        assertThat(persistedA.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(persistedA.getVersion()).isEqualTo(versionA);
        assertThat(persistedA.getUpdatedAt()).isEqualTo(updatedAtA);

        assertThat(persistedB.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(persistedB.getVersion()).isEqualTo(versionB);
        assertThat(persistedB.getUpdatedAt()).isEqualTo(updatedAtB);
    }

    @Test
    @DisplayName("Preview / Commit Consistency: Committed schedule exactly matches preview prediction")
    void shouldProduceExactSameScheduleOnCommitAsPreviewed() throws Exception {
        // Linear chain: A -> B -> C
        Task taskA = createTask("Task A", LocalDate.of(2026, 6, 1), 3);
        Task taskB = createTask("Task B", LocalDate.of(2026, 6, 4), 2);
        Task taskC = createTask("Task C", LocalDate.of(2026, 6, 6), 2);
        link(taskA, taskB);
        link(taskB, taskC);

        LocalDate proposedPlannedStart = LocalDate.of(2026, 6, 5);

        // 1. Run preview
        SchedulePreviewRequest previewReq = new SchedulePreviewRequest(proposedPlannedStart);
        MvcResult previewResult = mockMvc.perform(post("/api/tasks/{taskId}/schedule/preview", taskA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andReturn();

        ScheduleImpactPreviewResponse previewResponse = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(),
                ScheduleImpactPreviewResponse.class
        );

        TaskScheduleImpactDto previewA = previewResponse.tasks().get(0);
        TaskScheduleImpactDto previewB = previewResponse.tasks().get(1);
        TaskScheduleImpactDto previewC = previewResponse.tasks().get(2);

        // 2. Commit actual schedule update via PUT /api/tasks/{id}
        UpdateTaskRequest updateReq = new UpdateTaskRequest(
                taskA.getTitle(),
                taskA.getDescription(),
                taskA.getWorkflowStatus(),
                proposedPlannedStart,
                proposedPlannedStart,
                null,
                taskA.getDurationDays()
        );

        mockMvc.perform(put("/api/tasks/{id}", taskA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // 3. Verify database state matches the preview predictions EXACTLY
        Task committedA = taskRepository.findById(taskA.getId()).orElseThrow();
        Task committedB = taskRepository.findById(taskB.getId()).orElseThrow();
        Task committedC = taskRepository.findById(taskC.getId()).orElseThrow();

        // Exact match with preview predictions
        assertThat(committedA.getScheduledStartDate()).isEqualTo(previewA.proposedScheduledStart());
        assertThat(committedA.getScheduledDueDate()).isEqualTo(previewA.proposedScheduledDue());

        assertThat(committedB.getScheduledStartDate()).isEqualTo(previewB.proposedScheduledStart());
        assertThat(committedB.getScheduledDueDate()).isEqualTo(previewB.proposedScheduledDue());

        assertThat(committedC.getScheduledStartDate()).isEqualTo(previewC.proposedScheduledStart());
        assertThat(committedC.getScheduledDueDate()).isEqualTo(previewC.proposedScheduledDue());
    }

    @Test
    @DisplayName("Converging path integration: Diamond graph A -> B -> D and A -> C -> D previews and commits +3 days (not +6)")
    void shouldPreviewAndCommitConvergingPathWithoutCompounding() throws Exception {
        // A (June 1 - 3)
        // B (June 4 - 5)
        // C (June 4 - 5)
        // D (June 6 - 7)
        Task taskA = createTask("Task A", LocalDate.of(2026, 6, 1), 3);
        Task taskB = createTask("Task B", LocalDate.of(2026, 6, 4), 2);
        Task taskC = createTask("Task C", LocalDate.of(2026, 6, 4), 2);
        Task taskD = createTask("Task D", LocalDate.of(2026, 6, 6), 2);

        link(taskA, taskB);
        link(taskA, taskC);
        link(taskB, taskD);
        link(taskC, taskD);

        // Move A +3 days: June 4
        LocalDate proposedDate = LocalDate.of(2026, 6, 4);
        SchedulePreviewRequest previewReq = new SchedulePreviewRequest(proposedDate);

        MvcResult previewResult = mockMvc.perform(post("/api/tasks/{taskId}/schedule/preview", taskA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.maximumDelayDays").value(3))
                .andExpect(jsonPath("$.tasks[3].shiftDays").value(3))
                .andExpect(jsonPath("$.tasks[3].proposedScheduledStart").value("2026-06-09"))
                .andExpect(jsonPath("$.tasks[3].proposedScheduledDue").value("2026-06-10"))
                .andReturn();

        ScheduleImpactPreviewResponse previewResponse = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(),
                ScheduleImpactPreviewResponse.class
        );

        TaskScheduleImpactDto previewD = previewResponse.tasks().get(3);
        assertThat(previewD.shiftDays()).isEqualTo(3);
        assertThat(previewD.shiftDays()).isNotEqualTo(6);
        assertThat(previewD.constraintSourceTaskIds()).containsExactlyInAnyOrder(taskB.getId(), taskC.getId());

        // Commit change
        UpdateTaskRequest updateReq = new UpdateTaskRequest(
                taskA.getTitle(),
                taskA.getDescription(),
                taskA.getWorkflowStatus(),
                proposedDate,
                proposedDate,
                null,
                taskA.getDurationDays()
        );

        mockMvc.perform(put("/api/tasks/{id}", taskA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Verify committed D in DB
        Task committedD = taskRepository.findById(taskD.getId()).orElseThrow();
        assertThat(committedD.getScheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(committedD.getScheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(committedD.getScheduledStartDate()).isEqualTo(previewD.proposedScheduledStart());
    }

    @Test
    @DisplayName("Non-existent task ID returns structured 404 NOT FOUND")
    void shouldReturn404ForUnknownTaskId() throws Exception {
        UUID unknownId = UUID.randomUUID();
        SchedulePreviewRequest previewReq = new SchedulePreviewRequest(LocalDate.of(2026, 6, 4));

        mockMvc.perform(post("/api/tasks/{taskId}/schedule/preview", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Missing planned start date returns structured 400 BAD REQUEST validation error")
    void shouldReturn400ForMissingPlannedStartDate() throws Exception {
        Task taskA = createTask("Task A", LocalDate.of(2026, 6, 1), 3);

        String invalidBody = "{}";

        mockMvc.perform(post("/api/tasks/{taskId}/schedule/preview", taskA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
