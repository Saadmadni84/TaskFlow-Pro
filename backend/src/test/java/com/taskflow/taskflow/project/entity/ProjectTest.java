package com.taskflow.taskflow.project.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectTest {

    @Test
    @DisplayName("Should create project with trimmed name and description")
    void shouldCreateProjectSuccessfully() {
        Project project = new Project("  TaskFlow Core  ", "Main scheduling engine");

        assertThat(project.getId()).isNotNull();
        assertThat(project.getName()).isEqualTo("TaskFlow Core");
        assertThat(project.getDescription()).isEqualTo("Main scheduling engine");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should reject blank or null project name")
    void shouldRejectBlankProjectName(String invalidName) {
        assertThatThrownBy(() -> new Project(invalidName, "Description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project name must not be blank");
    }
}
