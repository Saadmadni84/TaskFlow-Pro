package com.taskflow.taskflow.dependency.entity;

import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskDependencyTest {

    private Project project;
    private Task taskA;
    private Task taskB;

    @BeforeEach
    void setUp() {
        project = new Project("DAG Project", "Workflow testing");
        taskA = new Task(UUID.randomUUID(), project, "Task A", "Predecessor", TaskStatus.DONE, null, null, null, null);
        taskB = new Task(UUID.randomUUID(), project, "Task B", "Successor", TaskStatus.BACKLOG, null, null, null, null);
    }

    @Test
    @DisplayName("Should create directed dependency relationship A -> B")
    void shouldCreateDirectedDependency() {
        TaskDependency dependency = new TaskDependency(taskA, taskB);

        assertThat(dependency.getId()).isNotNull();
        assertThat(dependency.getPredecessor()).isEqualTo(taskA);
        assertThat(dependency.getSuccessor()).isEqualTo(taskB);
    }

    @Test
    @DisplayName("Should reject self-dependency (task depending on itself)")
    void shouldRejectSelfDependency() {
        assertThatThrownBy(() -> new TaskDependency(taskA, taskA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-dependency is forbidden");
    }

    @Test
    @DisplayName("Should reject null predecessor or successor")
    void shouldRejectNullTasks() {
        assertThatThrownBy(() -> new TaskDependency(null, taskB))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Predecessor task must not be null");

        assertThatThrownBy(() -> new TaskDependency(taskA, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Successor task must not be null");
    }
}
