package com.taskflow.taskflow.task.entity;

import com.taskflow.taskflow.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project("Test Project", "Project for task unit tests");
    }

    @Test
    @DisplayName("Should create task with default workflow and dependency statuses")
    void shouldCreateTaskWithDefaults() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate due = LocalDate.of(2026, 10, 15);

        Task task = new Task(project, "  Core DAG Engine  ", "Build graph", null, start, due, 14);

        assertThat(task.getId()).isNotNull();
        assertThat(task.getTitle()).isEqualTo("Core DAG Engine");
        assertThat(task.getDescription()).isEqualTo("Build graph");
        assertThat(task.getWorkflowStatus()).isEqualTo(TaskStatus.BACKLOG);
        assertThat(task.getDependencyStatus()).isEqualTo(DependencyStatus.READY);
        assertThat(task.getStartDate()).isEqualTo(start);
        assertThat(task.getDueDate()).isEqualTo(due);
        assertThat(task.getDurationDays()).isEqualTo(14);
        assertThat(task.getProject()).isEqualTo(project);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Should reject blank or null task title")
    void shouldRejectBlankTaskTitle(String invalidTitle) {
        assertThatThrownBy(() -> new Task(project, invalidTitle, "Desc", TaskStatus.BACKLOG, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task title must not be blank");
    }

    @Test
    @DisplayName("Should reject start date after due date")
    void shouldRejectStartDateAfterDueDate() {
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate due = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> new Task(project, "Task A", "Desc", TaskStatus.BACKLOG, start, due, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be after due date");
    }

    @Test
    @DisplayName("Should reject negative duration days")
    void shouldRejectNegativeDurationDays() {
        assertThatThrownBy(() -> new Task(project, "Task A", "Desc", TaskStatus.BACKLOG, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Should reject task without a valid project")
    void shouldRejectTaskWithoutProject() {
        assertThatThrownBy(() -> new Task(null, "Task A", "Desc", TaskStatus.BACKLOG, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Project must not be null");
    }

    @Test
    @DisplayName("Should allow valid date equality (single day task)")
    void shouldAllowSameStartAndDueDate() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        Task task = new Task(project, "Milestone", "1-day event", TaskStatus.IN_PROGRESS, date, date, 1);

        assertThat(task.getStartDate()).isEqualTo(date);
        assertThat(task.getDueDate()).isEqualTo(date);
        assertThat(task.getDurationDays()).isEqualTo(1);
    }
}
