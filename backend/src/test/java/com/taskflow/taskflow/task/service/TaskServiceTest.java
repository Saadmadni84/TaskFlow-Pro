package com.taskflow.taskflow.task.service;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.dependency.readiness.DependencyReadinessService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.scheduling.service.SchedulingService;
import com.taskflow.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.taskflow.task.dto.TaskResponse;
import com.taskflow.taskflow.task.dto.UpdateTaskRequest;
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

import java.time.LocalDate;
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
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DependencyReadinessService readinessService;

    @Mock
    private SchedulingService schedulingService;

    @InjectMocks
    private TaskService taskService;

    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        project = new Project(UUID.randomUUID(), "Test Project", "Description");
        task = new Task(UUID.randomUUID(), project, "Initial Title", "Initial Description",
                TaskStatus.IN_PROGRESS, DependencyStatus.READY, null, null, null);
    }

    @Test
    @DisplayName("Should create task with default READY dependency status")
    void shouldCreateTaskSuccessfully() {
        CreateTaskRequest request = new CreateTaskRequest(project.getId(), "New Task", "Desc", TaskStatus.BACKLOG, null, null, null);

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.createTask(request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("New Task");
        assertThat(response.workflowStatus()).isEqualTo(TaskStatus.BACKLOG);
        assertThat(response.dependencyStatus()).isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Should trigger readiness propagation when transitioning from non-DONE to DONE")
    void shouldTriggerReadinessWhenTaskBecomesDone() {
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        UpdateTaskRequest request = new UpdateTaskRequest("New Title", "New Desc", TaskStatus.DONE, null, null, null);
        TaskResponse response = taskService.updateTask(task.getId(), request);

        assertThat(response.workflowStatus()).isEqualTo(TaskStatus.DONE);
        verify(readinessService).recalculateAffectedDescendants(task.getId());
    }

    @Test
    @DisplayName("Should trigger readiness propagation when transitioning from DONE to non-DONE (rollback)")
    void shouldTriggerReadinessWhenDoneTaskIsReopened() {
        task.setWorkflowStatus(TaskStatus.DONE);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        UpdateTaskRequest request = new UpdateTaskRequest("New Title", "New Desc", TaskStatus.IN_PROGRESS, null, null, null);
        TaskResponse response = taskService.updateTask(task.getId(), request);

        assertThat(response.workflowStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(readinessService).recalculateAffectedDescendants(task.getId());
    }

    @Test
    @DisplayName("Should NOT trigger readiness propagation when transitioning between non-DONE statuses (BACKLOG -> IN_PROGRESS)")
    void shouldNotTriggerReadinessWhenCompletionStateUnchanged() {
        task.setWorkflowStatus(TaskStatus.BACKLOG);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        UpdateTaskRequest request = new UpdateTaskRequest("New Title", "New Desc", TaskStatus.IN_PROGRESS, null, null, null);
        TaskResponse response = taskService.updateTask(task.getId(), request);

        assertThat(response.workflowStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(readinessService, never()).recalculateAffectedDescendants(any());
    }

    @Test
    @DisplayName("Should trigger schedulingService when planned start or duration changes")
    void shouldTriggerSchedulingWhenDatesChange() {
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        LocalDate newStart = LocalDate.of(2026, 6, 1);

        UpdateTaskRequest request = new UpdateTaskRequest("Title", "Desc", null, newStart, newStart, null, 5);
        taskService.updateTask(task.getId(), request);

        verify(schedulingService).updateTaskSchedule(eq(task), eq(newStart), eq(5));
    }

    @Test
    @DisplayName("Should throw TaskNotFoundException when updating non-existent task")
    void shouldThrowTaskNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(taskRepository.findById(unknownId)).thenReturn(Optional.empty());

        UpdateTaskRequest request = new UpdateTaskRequest("Title", "Desc", TaskStatus.DONE, null, null, null);

        assertThatThrownBy(() -> taskService.updateTask(unknownId, request))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}
