package com.taskflow.taskflow.criticalpath.controller;

import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.scheduling.service.SchedulingService;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CriticalPathControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private SchedulingService schedulingService;

    private Project project;
    private Task taskA;
    private Task taskB;
    private Task taskC;
    private Task taskD;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(new Project("Critical Path Project", "Testing Critical Path Analysis"));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/critical-path returns 404 for non-existent project")
    void shouldReturn404ForNonExistentProject() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/api/projects/{projectId}/critical-path", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/critical-path returns defined empty response for empty project")
    void shouldReturnEmptyResponseForEmptyProject() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/critical-path", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", is(project.getId().toString())))
                .andExpect(jsonPath("$.criticalTaskIds", hasSize(0)))
                .andExpect(jsonPath("$.criticalPaths", hasSize(0)))
                .andExpect(jsonPath("$.tasks", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/critical-path computes critical path and is strictly side-effect free")
    void shouldComputeCriticalPathAndBeSideEffectFree() throws Exception {
        // Setup: A -> B -> D (3 + 5 + 4 = 12) vs A -> C -> D (3 + 2 + 4 = 9)
        LocalDate start = LocalDate.of(2026, 6, 1);
        taskA = taskRepository.saveAndFlush(new Task(project, "Task A", "Design", TaskStatus.BACKLOG, start, start.plusDays(2), 3));
        taskB = taskRepository.saveAndFlush(new Task(project, "Task B", "Backend", TaskStatus.BACKLOG, start.plusDays(3), start.plusDays(7), 5));
        taskC = taskRepository.saveAndFlush(new Task(project, "Task C", "Frontend", TaskStatus.BACKLOG, start.plusDays(3), start.plusDays(4), 2));
        taskD = taskRepository.saveAndFlush(new Task(project, "Task D", "QA", TaskStatus.BACKLOG, start.plusDays(8), start.plusDays(11), 4));

        dependencyRepository.saveAndFlush(new TaskDependency(taskA, taskB));
        dependencyRepository.saveAndFlush(new TaskDependency(taskA, taskC));
        dependencyRepository.saveAndFlush(new TaskDependency(taskB, taskD));
        dependencyRepository.saveAndFlush(new TaskDependency(taskC, taskD));

        Long versionA = taskA.getVersion();
        Long versionB = taskB.getVersion();
        Long versionC = taskC.getVersion();
        Long versionD = taskD.getVersion();

        mockMvc.perform(get("/api/projects/{projectId}/critical-path", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", is(project.getId().toString())))
                .andExpect(jsonPath("$.projectCompletionDate", is("2026-06-12")))
                .andExpect(jsonPath("$.criticalTaskIds", hasSize(3)))
                .andExpect(jsonPath("$.criticalPaths", hasSize(1)))
                .andExpect(jsonPath("$.criticalPaths[0][0]", is(taskA.getId().toString())))
                .andExpect(jsonPath("$.criticalPaths[0][1]", is(taskB.getId().toString())))
                .andExpect(jsonPath("$.criticalPaths[0][2]", is(taskD.getId().toString())))
                .andExpect(jsonPath("$.tasks", hasSize(4)));

        // Side-effect free verification: database entities and versions remain untouched
        Task reloadedA = taskRepository.findById(taskA.getId()).orElseThrow();
        Task reloadedB = taskRepository.findById(taskB.getId()).orElseThrow();
        Task reloadedC = taskRepository.findById(taskC.getId()).orElseThrow();
        Task reloadedD = taskRepository.findById(taskD.getId()).orElseThrow();

        assertThat(reloadedA.getVersion()).isEqualTo(versionA);
        assertThat(reloadedB.getVersion()).isEqualTo(versionB);
        assertThat(reloadedC.getVersion()).isEqualTo(versionC);
        assertThat(reloadedD.getVersion()).isEqualTo(versionD);
        assertThat(dependencyRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Critical Path updates dynamically when schedule shifts")
    void shouldReflectUpdatedScheduleDynamically() throws Exception {
        LocalDate start = LocalDate.of(2026, 6, 1);
        taskA = taskRepository.saveAndFlush(new Task(project, "Task A", "Design", TaskStatus.BACKLOG, start, start.plusDays(2), 3));
        taskB = taskRepository.saveAndFlush(new Task(project, "Task B", "Backend", TaskStatus.BACKLOG, start.plusDays(3), start.plusDays(7), 5));
        taskC = taskRepository.saveAndFlush(new Task(project, "Task C", "Frontend", TaskStatus.BACKLOG, start.plusDays(3), start.plusDays(4), 2));
        taskD = taskRepository.saveAndFlush(new Task(project, "Task D", "QA", TaskStatus.BACKLOG, start.plusDays(8), start.plusDays(11), 4));

        dependencyRepository.saveAndFlush(new TaskDependency(taskA, taskB));
        dependencyRepository.saveAndFlush(new TaskDependency(taskA, taskC));
        dependencyRepository.saveAndFlush(new TaskDependency(taskB, taskD));
        dependencyRepository.saveAndFlush(new TaskDependency(taskC, taskD));

        // Initial critical path: A -> B -> D
        mockMvc.perform(get("/api/projects/{projectId}/critical-path", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalPaths[0][1]", is(taskB.getId().toString())));

        // Shift task C's duration from 2 to 10 days using SchedulingService
        schedulingService.updateTaskSchedule(taskC, taskC.getPlannedStartDate(), 10);

        // Now branch C is 3 + 10 = 13 days > branch B (3 + 5 = 8 days).
        // Critical path must now be A -> C -> D!
        mockMvc.perform(get("/api/projects/{projectId}/critical-path", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalPaths[0][0]", is(taskA.getId().toString())))
                .andExpect(jsonPath("$.criticalPaths[0][1]", is(taskC.getId().toString())))
                .andExpect(jsonPath("$.criticalPaths[0][2]", is(taskD.getId().toString())));
    }
}
