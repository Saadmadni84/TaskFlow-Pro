package com.taskflow.taskflow.scheduling.service;

import com.taskflow.taskflow.scheduling.model.ScheduleCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ScheduleCalculationService Pure Domain Unit Tests")
class ScheduleCalculationServiceTest {

    private ScheduleCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new ScheduleCalculationService();
    }

    @Test
    @DisplayName("Test 1: Root task (no predecessors)")
    void shouldScheduleRootTaskAtPlannedStart() {
        LocalDate plannedStart = LocalDate.of(2026, 6, 10);
        int duration = 3;

        ScheduleCalculationResult result = calculationService.calculateSchedule(
                plannedStart,
                duration,
                Collections.emptyList()
        );

        assertThat(result.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(result.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(result.durationDays()).isEqualTo(3);
        assertThat(result.latestPredecessorConstraint()).isNull();
        assertThat(result.hasShift()).isFalse();
    }

    @Test
    @DisplayName("Test 2: Single predecessor forces successor later")
    void shouldShiftSuccessorToRespectSinglePredecessor() {
        LocalDate aDue = LocalDate.of(2026, 6, 10);
        LocalDate bPlannedStart = LocalDate.of(2026, 6, 5);
        int bDuration = 3;

        ScheduleCalculationResult result = calculationService.calculateSchedule(
                bPlannedStart,
                bDuration,
                List.of(aDue)
        );

        // Required start = June 10 + 1 day = June 11
        assertThat(result.latestPredecessorConstraint()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(result.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(result.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(result.durationDays()).isEqualTo(3);
        assertThat(result.hasShift()).isTrue();
    }

    @Test
    @DisplayName("Test 3: Planned date already later than predecessor constraint (not pulled earlier)")
    void shouldNotPullTaskEarlierWhenPlannedDateIsAlreadyLater() {
        LocalDate aDue = LocalDate.of(2026, 6, 10);
        LocalDate bPlannedStart = LocalDate.of(2026, 6, 15);
        int bDuration = 3;

        ScheduleCalculationResult result = calculationService.calculateSchedule(
                bPlannedStart,
                bDuration,
                List.of(aDue)
        );

        // Constraint is June 11, but planned is June 15
        assertThat(result.latestPredecessorConstraint()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(result.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(result.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(result.hasShift()).isFalse();
    }

    @Test
    @DisplayName("Test 4: Multiple predecessors (respects maximum required constraint)")
    void shouldRespectLatestPredecessorConstraintAmongMultiple() {
        LocalDate aDue = LocalDate.of(2026, 6, 10); // constraint: June 11
        LocalDate bDue = LocalDate.of(2026, 6, 12); // constraint: June 13
        LocalDate cDue = LocalDate.of(2026, 6, 11); // constraint: June 12

        LocalDate dPlannedStart = LocalDate.of(2026, 6, 1);
        int dDuration = 4;

        ScheduleCalculationResult result = calculationService.calculateSchedule(
                dPlannedStart,
                dDuration,
                List.of(aDue, bDue, cDue)
        );

        assertThat(result.latestPredecessorConstraint()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(result.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(result.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(result.durationDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("Test 5: Converging paths do not compound delays (A -> B -> D, A -> C -> D)")
    void shouldNotCompoundDelaysInConvergingGraph() {
        // Initial schedule:
        // A: June 1 - June 3 (due June 3)
        // B: June 4 - June 5 (due June 5, duration 2)
        // C: June 4 - June 5 (due June 5, duration 2)
        // D: June 6 - June 8 (due June 8, duration 3, planned June 6)

        // Shift A by +3 days -> A due becomes June 6
        LocalDate newADue = LocalDate.of(2026, 6, 6);

        // B and C recalculate from new A:
        ScheduleCalculationResult bResult = calculationService.calculateSchedule(
                LocalDate.of(2026, 6, 4), 2, List.of(newADue)
        );
        assertThat(bResult.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(bResult.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 8));

        ScheduleCalculationResult cResult = calculationService.calculateSchedule(
                LocalDate.of(2026, 6, 4), 2, List.of(newADue)
        );
        assertThat(cResult.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(cResult.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 8));

        // D evaluates predecessors B and C:
        ScheduleCalculationResult dResult = calculationService.calculateSchedule(
                LocalDate.of(2026, 6, 6), 3, List.of(bResult.scheduledDueDate(), cResult.scheduledDueDate())
        );

        // D must start on June 9, due June 11 -> shifted exactly +3 days from June 6, NOT +6!
        assertThat(dResult.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(dResult.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(dResult.durationDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("Test 9: Earlier upstream change allows task to return to baseline planned start")
    void shouldReturnToPlannedStartWhenPredecessorConstraintIsRelaxed() {
        LocalDate bPlannedStart = LocalDate.of(2026, 6, 10);
        int bDuration = 3;

        // When predecessor A was forcing B to June 15:
        LocalDate aDueLate = LocalDate.of(2026, 6, 14);
        ScheduleCalculationResult resultLate = calculationService.calculateSchedule(bPlannedStart, bDuration, List.of(aDueLate));
        assertThat(resultLate.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));

        // When predecessor A moves earlier to June 7:
        LocalDate aDueEarly = LocalDate.of(2026, 6, 7);
        ScheduleCalculationResult resultEarly = calculationService.calculateSchedule(bPlannedStart, bDuration, List.of(aDueEarly));

        // B returns to its plannedStart June 10 because constraint is June 8 <= June 10
        assertThat(resultEarly.latestPredecessorConstraint()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(resultEarly.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(resultEarly.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 12));
    }

    @Test
    @DisplayName("Test 10: Duration preservation (durationDays remains constant across any shift)")
    void shouldPreserveDurationAcrossAnyShift() {
        LocalDate plannedStart = LocalDate.of(2026, 6, 10);
        int duration = 5;

        LocalDate predDue = LocalDate.of(2026, 6, 14);
        ScheduleCalculationResult result = calculationService.calculateSchedule(plannedStart, duration, List.of(predDue));

        assertThat(result.scheduledStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(result.scheduledDueDate()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(result.durationDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should reject invalid duration less than 1")
    void shouldRejectInvalidDuration() {
        assertThatThrownBy(() -> calculationService.calculateSchedule(LocalDate.of(2026, 6, 1), 0, Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationDays must be at least 1");
    }
}
