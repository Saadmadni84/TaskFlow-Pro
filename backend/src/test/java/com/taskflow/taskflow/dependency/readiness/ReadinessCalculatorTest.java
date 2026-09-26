package com.taskflow.taskflow.dependency.readiness;

import com.taskflow.taskflow.dependency.readiness.ReadinessCalculator.PredecessorState;
import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReadinessCalculator Pure Domain Tests")
class ReadinessCalculatorTest {

    @Test
    @DisplayName("No prerequisites -> READY")
    void shouldReturnReadyWhenNoPrerequisites() {
        assertThat(ReadinessCalculator.calculate(Collections.emptyList()))
                .isEqualTo(DependencyStatus.READY);

        assertThat(ReadinessCalculator.calculate(null))
                .isEqualTo(DependencyStatus.READY);

        assertThat(ReadinessCalculator.calculateFromStates(Collections.emptyList()))
                .isEqualTo(DependencyStatus.READY);

        assertThat(ReadinessCalculator.calculateFromStates(null))
                .isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("One incomplete prerequisite [A = IN_PROGRESS] -> BLOCKED")
    void shouldReturnBlockedWhenOneIncompletePrerequisite() {
        assertThat(ReadinessCalculator.calculate(List.of(TaskStatus.IN_PROGRESS)))
                .isEqualTo(DependencyStatus.BLOCKED);

        assertThat(ReadinessCalculator.calculateFromStates(List.of(PredecessorState.of(TaskStatus.IN_PROGRESS))))
                .isEqualTo(DependencyStatus.BLOCKED);
    }

    @Test
    @DisplayName("One incomplete prerequisite [A = BACKLOG] -> BLOCKED")
    void shouldReturnBlockedWhenPrerequisiteInBacklog() {
        assertThat(ReadinessCalculator.calculate(List.of(TaskStatus.BACKLOG)))
                .isEqualTo(DependencyStatus.BLOCKED);

        assertThat(ReadinessCalculator.calculateFromStates(List.of(PredecessorState.of(TaskStatus.BACKLOG))))
                .isEqualTo(DependencyStatus.BLOCKED);
    }

    @Test
    @DisplayName("One incomplete prerequisite [A = REVIEW] -> BLOCKED")
    void shouldReturnBlockedWhenPrerequisiteInReview() {
        assertThat(ReadinessCalculator.calculate(List.of(TaskStatus.REVIEW)))
                .isEqualTo(DependencyStatus.BLOCKED);

        assertThat(ReadinessCalculator.calculateFromStates(List.of(PredecessorState.of(TaskStatus.REVIEW))))
                .isEqualTo(DependencyStatus.BLOCKED);
    }

    @Test
    @DisplayName("One completed prerequisite [A = DONE] -> READY")
    void shouldReturnReadyWhenOneCompletedPrerequisite() {
        assertThat(ReadinessCalculator.calculate(List.of(TaskStatus.DONE)))
                .isEqualTo(DependencyStatus.READY);

        assertThat(ReadinessCalculator.calculateFromStates(List.of(PredecessorState.done())))
                .isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Multiple prerequisites [A = DONE, B = IN_PROGRESS] -> BLOCKED")
    void shouldReturnBlockedWhenAtLeastOnePrerequisiteIncomplete() {
        assertThat(ReadinessCalculator.calculate(List.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS)))
                .isEqualTo(DependencyStatus.BLOCKED);

        List<PredecessorState> states = List.of(
                PredecessorState.done(),
                PredecessorState.of(TaskStatus.IN_PROGRESS)
        );
        assertThat(ReadinessCalculator.calculateFromStates(states))
                .isEqualTo(DependencyStatus.BLOCKED);
    }

    @Test
    @DisplayName("Multiple completed prerequisites [A = DONE, B = DONE] -> READY")
    void shouldReturnReadyWhenAllPrerequisitesCompleted() {
        assertThat(ReadinessCalculator.calculate(List.of(TaskStatus.DONE, TaskStatus.DONE)))
                .isEqualTo(DependencyStatus.READY);

        List<PredecessorState> states = List.of(
                PredecessorState.done(),
                PredecessorState.done()
        );
        assertThat(ReadinessCalculator.calculateFromStates(states))
                .isEqualTo(DependencyStatus.READY);
    }

    @Test
    @DisplayName("Predecessor workflowStatus is DONE but dependencyStatus is BLOCKED -> BLOCKED")
    void shouldReturnBlockedWhenPredecessorIsDoneButBlocked() {
        PredecessorState doneButBlocked = PredecessorState.of(TaskStatus.DONE, DependencyStatus.BLOCKED);
        assertThat(ReadinessCalculator.calculateFromStates(List.of(doneButBlocked)))
                .isEqualTo(DependencyStatus.BLOCKED);
    }

    @Test
    @DisplayName("Converging predecessors: [DONE & READY, DONE & BLOCKED] -> BLOCKED")
    void shouldReturnBlockedWhenOneConvergingPredecessorIsBlocked() {
        List<PredecessorState> states = List.of(
                PredecessorState.of(TaskStatus.DONE, DependencyStatus.READY),
                PredecessorState.of(TaskStatus.DONE, DependencyStatus.BLOCKED)
        );
        assertThat(ReadinessCalculator.calculateFromStates(states))
                .isEqualTo(DependencyStatus.BLOCKED);
    }
}
