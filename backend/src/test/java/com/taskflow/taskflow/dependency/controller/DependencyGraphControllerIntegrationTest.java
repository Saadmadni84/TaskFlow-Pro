package com.taskflow.taskflow.dependency.controller;

import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class DependencyGraphControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private TaskDependencyService dependencyService;

    private Project project;
    private Task taskA;
    private Task taskB;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(new Project("Graph Test Project", "Verifying graph endpoint"));
        taskA = taskRepository.saveAndFlush(new Task(project, "Task A", "First", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), 5));
        taskB = taskRepository.saveAndFlush(new Task(project, "Task B", "Second", TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 8), 3));

        dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskB.getId()));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/dependency-graph returns 200 with nodes and directed edges")
    void shouldReturnDependencyGraph() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/dependency-graph", project.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", is(project.getId().toString())))
                .andExpect(jsonPath("$.nodes", hasSize(2)))
                .andExpect(jsonPath("$.edges", hasSize(1)))
                .andExpect(jsonPath("$.edges[0].predecessorTaskId", is(taskA.getId().toString())))
                .andExpect(jsonPath("$.edges[0].successorTaskId", is(taskB.getId().toString())));
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/dependency-graph returns 404 for unknown project")
    void shouldReturn404WhenProjectNotFound() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/projects/{projectId}/dependency-graph", randomId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }
}
