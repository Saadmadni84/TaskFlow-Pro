package com.taskflow.taskflow.criticalpath.graph;

import com.taskflow.taskflow.common.exception.CycleDetectedException;
import com.taskflow.taskflow.criticalpath.model.CriticalPathResult;
import com.taskflow.taskflow.criticalpath.model.TaskScheduleInput;
import com.taskflow.taskflow.criticalpath.model.TaskScheduleMetrics;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriticalPathCalculatorTest {

    private CriticalPathCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CriticalPathCalculator();
    }

    @Test
    @DisplayName("Scenario 1: Single task - has zero slack and forms a single-node critical path")
    void test1_singleTask() {
        UUID taskId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);
        TaskScheduleInput task = new TaskScheduleInput(taskId, "Single Task", start, start.plusDays(4), start, 5);

        DependencyGraph graph = new DependencyGraph();
        graph.addNode(taskId);

        CriticalPathResult result = calculator.calculate(List.of(task), graph);

        assertThat(result.projectStartDate()).isEqualTo(start);
        assertThat(result.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(result.criticalTaskIds()).containsExactly(taskId);
        assertThat(result.criticalPaths()).hasSize(1);
        assertThat(result.criticalPaths().get(0)).containsExactly(taskId);

        TaskScheduleMetrics metrics = result.taskMetrics().get(0);
        assertThat(metrics.earliestStart()).isEqualTo(start);
        assertThat(metrics.earliestFinish()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(metrics.latestStart()).isEqualTo(start);
        assertThat(metrics.latestFinish()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(metrics.totalSlackDays()).isZero();
        assertThat(metrics.isCritical()).isTrue();
    }

    @Test
    @DisplayName("Scenario 2: Linear chain A -> B -> C - all tasks are critical with zero slack")
    void test2_linearChain() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput taskA = new TaskScheduleInput(aId, "Task A", start, null, start, 3);
        TaskScheduleInput taskB = new TaskScheduleInput(bId, "Task B", null, null, null, 2);
        TaskScheduleInput taskC = new TaskScheduleInput(cId, "Task C", null, null, null, 4);

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(bId, cId);

        CriticalPathResult result = calculator.calculate(List.of(taskA, taskB, taskC), graph);

        // A: 06-01 to 06-03
        // B: 06-04 to 06-05
        // C: 06-06 to 06-09
        assertThat(result.projectStartDate()).isEqualTo(start);
        assertThat(result.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(result.criticalTaskIds()).containsExactly(aId, bId, cId);
        assertThat(result.criticalPaths()).hasSize(1);
        assertThat(result.criticalPaths().get(0)).containsExactly(aId, bId, cId);

        Map<UUID, TaskScheduleMetrics> metricsMap = result.taskMetrics().stream()
                .collect(Collectors.toMap(TaskScheduleMetrics::taskId, Function.identity()));

        assertThat(metricsMap.get(aId).totalSlackDays()).isZero();
        assertThat(metricsMap.get(bId).totalSlackDays()).isZero();
        assertThat(metricsMap.get(cId).totalSlackDays()).isZero();
    }

    @Test
    @DisplayName("Scenario 3: Branching A -> B (5 days) and A -> C (2 days) - longer branch B is critical")
    void test3_branching() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput taskA = new TaskScheduleInput(aId, "Task A", start, null, start, 3);
        TaskScheduleInput taskB = new TaskScheduleInput(bId, "Task B", null, null, null, 5); // Longer
        TaskScheduleInput taskC = new TaskScheduleInput(cId, "Task C", null, null, null, 2); // Shorter

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(aId, cId);

        CriticalPathResult result = calculator.calculate(List.of(taskA, taskB, taskC), graph);

        // A: 06-01 to 06-03
        // B: 06-04 to 06-08 (completion 06-08)
        // C: 06-04 to 06-05 (slack = 06-08 - 06-05 = 3 days)
        assertThat(result.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(result.criticalTaskIds()).containsExactlyInAnyOrder(aId, bId);
        assertThat(result.criticalPaths()).hasSize(1);
        assertThat(result.criticalPaths().get(0)).containsExactly(aId, bId);

        Map<UUID, TaskScheduleMetrics> metricsMap = result.taskMetrics().stream()
                .collect(Collectors.toMap(TaskScheduleMetrics::taskId, Function.identity()));

        assertThat(metricsMap.get(bId).isCritical()).isTrue();
        assertThat(metricsMap.get(bId).totalSlackDays()).isZero();

        assertThat(metricsMap.get(cId).isCritical()).isFalse();
        assertThat(metricsMap.get(cId).totalSlackDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("Scenario 4: Converging A -> B -> D and A -> C -> D with B > C - A -> B -> D is critical")
    void test4_converging() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        UUID dId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput taskA = new TaskScheduleInput(aId, "Task A", start, null, start, 3);
        TaskScheduleInput taskB = new TaskScheduleInput(bId, "Task B", null, null, null, 5);
        TaskScheduleInput taskC = new TaskScheduleInput(cId, "Task C", null, null, null, 2);
        TaskScheduleInput taskD = new TaskScheduleInput(dId, "Task D", null, null, null, 4);

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(aId, cId);
        graph.addEdge(bId, dId);
        graph.addEdge(cId, dId);

        CriticalPathResult result = calculator.calculate(List.of(taskA, taskB, taskC, taskD), graph);

        // A: 06-01 to 06-03
        // B: 06-04 to 06-08
        // C: 06-04 to 06-05
        // D: 06-09 to 06-12
        assertThat(result.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(result.criticalTaskIds()).containsExactlyInAnyOrder(aId, bId, dId);
        assertThat(result.criticalPaths()).hasSize(1);
        assertThat(result.criticalPaths().get(0)).containsExactly(aId, bId, dId);

        Map<UUID, TaskScheduleMetrics> metricsMap = result.taskMetrics().stream()
                .collect(Collectors.toMap(TaskScheduleMetrics::taskId, Function.identity()));

        assertThat(metricsMap.get(cId).isCritical()).isFalse();
        assertThat(metricsMap.get(cId).totalSlackDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("Scenario 5: Equal branches A -> B -> D and A -> C -> D - multiple critical paths returned")
    void test5_equalBranchesMultipleCriticalPaths() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        UUID dId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput taskA = new TaskScheduleInput(aId, "Task A", start, null, start, 3);
        TaskScheduleInput taskB = new TaskScheduleInput(bId, "Task B", null, null, null, 5);
        TaskScheduleInput taskC = new TaskScheduleInput(cId, "Task C", null, null, null, 5); // Equal to B
        TaskScheduleInput taskD = new TaskScheduleInput(dId, "Task D", null, null, null, 4);

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(aId, cId);
        graph.addEdge(bId, dId);
        graph.addEdge(cId, dId);

        CriticalPathResult result = calculator.calculate(List.of(taskA, taskB, taskC, taskD), graph);

        assertThat(result.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(result.criticalTaskIds()).containsExactlyInAnyOrder(aId, bId, cId, dId);
        assertThat(result.criticalPaths()).hasSize(2);

        List<List<UUID>> paths = result.criticalPaths();
        assertThat(paths).contains(List.of(aId, bId, dId));
        assertThat(paths).contains(List.of(aId, cId, dId));
    }

    @Test
    @DisplayName("Scenario 6: Multiple roots A -> B and X -> Y - later terminal finish determines completion")
    void test6_multipleRoots() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID xId = UUID.randomUUID();
        UUID yId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput taskA = new TaskScheduleInput(aId, "Task A", start, null, start, 3);
        TaskScheduleInput taskB = new TaskScheduleInput(bId, "Task B", null, null, null, 5); // Finishes 06-08

        TaskScheduleInput taskX = new TaskScheduleInput(xId, "Task X", start, null, start, 2);
        TaskScheduleInput taskY = new TaskScheduleInput(yId, "Task Y", null, null, null, 2); // Finishes 06-04

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(xId, yId);

        CriticalPathResult result = calculator.calculate(List.of(taskA, taskB, taskX, taskY), graph);

        assertThat(result.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(result.criticalTaskIds()).containsExactlyInAnyOrder(aId, bId);
        assertThat(result.criticalPaths()).hasSize(1);
        assertThat(result.criticalPaths().get(0)).containsExactly(aId, bId);

        Map<UUID, TaskScheduleMetrics> metricsMap = result.taskMetrics().stream()
                .collect(Collectors.toMap(TaskScheduleMetrics::taskId, Function.identity()));

        assertThat(metricsMap.get(xId).totalSlackDays()).isEqualTo(4);
        assertThat(metricsMap.get(yId).totalSlackDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("Scenario 7: Multiple terminal tasks - latest terminal determines project completion")
    void test7_multipleTerminalTasks() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput taskA = new TaskScheduleInput(aId, "Task A", start, null, start, 2);
        TaskScheduleInput taskB = new TaskScheduleInput(bId, "Task B", null, null, null, 3); // Finishes 06-05
        TaskScheduleInput taskC = new TaskScheduleInput(cId, "Task C", null, null, null, 8); // Finishes 06-10

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(aId, cId);

        CriticalPathResult result = calculator.calculate(List.of(taskA, taskB, taskC), graph);

        assertThat(result.projectCompletionDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(result.criticalTaskIds()).containsExactlyInAnyOrder(aId, cId);
    }

    @Test
    @DisplayName("Scenario 8: Unrelated subgraphs - all tasks are included in metrics")
    void test8_unrelatedSubgraphs() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID isolatedId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput taskA = new TaskScheduleInput(aId, "Task A", start, null, start, 4);
        TaskScheduleInput taskB = new TaskScheduleInput(bId, "Task B", null, null, null, 4);
        TaskScheduleInput taskIso = new TaskScheduleInput(isolatedId, "Isolated Task", start, null, start, 2);

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addNode(isolatedId);

        CriticalPathResult result = calculator.calculate(List.of(taskA, taskB, taskIso), graph);

        assertThat(result.taskMetrics()).hasSize(3);
        Map<UUID, TaskScheduleMetrics> metricsMap = result.taskMetrics().stream()
                .collect(Collectors.toMap(TaskScheduleMetrics::taskId, Function.identity()));

        assertThat(metricsMap).containsKey(isolatedId);
        assertThat(metricsMap.get(isolatedId).isCritical()).isFalse();
        assertThat(metricsMap.get(isolatedId).totalSlackDays()).isEqualTo(6);
    }

    @Test
    @DisplayName("Scenario 9 & 10: Date arithmetic inclusive tests for duration 1 and 3")
    void test9_10_dateArithmetic() {
        UUID t1 = UUID.randomUUID();
        UUID t3 = UUID.randomUUID();
        LocalDate june10 = LocalDate.of(2026, 6, 10);

        TaskScheduleInput task1 = new TaskScheduleInput(t1, "Task 1", june10, null, june10, 1);
        TaskScheduleInput task3 = new TaskScheduleInput(t3, "Task 3", june10, null, june10, 3);

        DependencyGraph graph = new DependencyGraph();
        graph.addNode(t1);
        graph.addNode(t3);

        CriticalPathResult result = calculator.calculate(List.of(task1, task3), graph);

        Map<UUID, TaskScheduleMetrics> map = result.taskMetrics().stream()
                .collect(Collectors.toMap(TaskScheduleMetrics::taskId, Function.identity()));

        // Duration 1: June 10 -> June 10
        assertThat(map.get(t1).earliestStart()).isEqualTo(june10);
        assertThat(map.get(t1).earliestFinish()).isEqualTo(june10);

        // Duration 3: June 10 -> June 12
        assertThat(map.get(t3).earliestStart()).isEqualTo(june10);
        assertThat(map.get(t3).earliestFinish()).isEqualTo(LocalDate.of(2026, 6, 12));
    }

    @Test
    @DisplayName("Scenario 11: Dependency gap - predecessor finish June 12, successor start June 13")
    void test11_dependencyGap() {
        UUID pId = UUID.randomUUID();
        UUID sId = UUID.randomUUID();
        LocalDate june10 = LocalDate.of(2026, 6, 10);

        TaskScheduleInput pred = new TaskScheduleInput(pId, "Predecessor", june10, null, june10, 3); // June 10-12
        TaskScheduleInput succ = new TaskScheduleInput(sId, "Successor", null, null, null, 2);

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(pId, sId);

        CriticalPathResult result = calculator.calculate(List.of(pred, succ), graph);

        Map<UUID, TaskScheduleMetrics> map = result.taskMetrics().stream()
                .collect(Collectors.toMap(TaskScheduleMetrics::taskId, Function.identity()));

        assertThat(map.get(pId).earliestFinish()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(map.get(sId).earliestStart()).isEqualTo(LocalDate.of(2026, 6, 13));
    }

    @Test
    @DisplayName("Scenario 12 & 13: Positive slack and zero slack validation")
    void test12_13_slackFormulations() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput a = new TaskScheduleInput(aId, "A", start, null, start, 2);
        TaskScheduleInput b = new TaskScheduleInput(bId, "B", null, null, null, 5); // Critical
        TaskScheduleInput c = new TaskScheduleInput(cId, "C", null, null, null, 1); // Non-critical

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(aId, cId);

        CriticalPathResult result = calculator.calculate(List.of(a, b, c), graph);

        for (TaskScheduleMetrics m : result.taskMetrics()) {
            // Verify both slack formulations yield the identical result
            long slackFromStart = java.time.temporal.ChronoUnit.DAYS.between(m.earliestStart(), m.latestStart());
            long slackFromFinish = java.time.temporal.ChronoUnit.DAYS.between(m.earliestFinish(), m.latestFinish());
            assertThat(slackFromStart).isEqualTo(slackFromFinish);
            assertThat(m.totalSlackDays()).isEqualTo(slackFromStart);
        }
    }

    @Test
    @DisplayName("Scenario 14: Invalid cycle - throws controlled CycleDetectedException")
    void test14_invalidCycle() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput a = new TaskScheduleInput(aId, "A", start, null, start, 2);
        TaskScheduleInput b = new TaskScheduleInput(bId, "B", start, null, start, 2);

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(bId, aId); // Cycle: A -> B -> A

        assertThatThrownBy(() -> calculator.calculate(List.of(a, b), graph))
                .isInstanceOf(CycleDetectedException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    @DisplayName("Scenario 15: Empty project - returns empty result without crashing")
    void test15_emptyProject() {
        CriticalPathResult result = calculator.calculate(List.of(), new DependencyGraph());

        assertThat(result.projectStartDate()).isNull();
        assertThat(result.projectCompletionDate()).isNull();
        assertThat(result.criticalTaskIds()).isEmpty();
        assertThat(result.criticalPaths()).isEmpty();
        assertThat(result.taskMetrics()).isEmpty();
    }

    @Test
    @DisplayName("Scenario 16: Deterministic ordering - identical input produces identical output across repeated runs")
    void test16_deterministicOrdering() {
        UUID aId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID bId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID cId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID dId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        LocalDate start = LocalDate.of(2026, 6, 1);

        TaskScheduleInput taskA = new TaskScheduleInput(aId, "A", start, null, start, 3);
        TaskScheduleInput taskB = new TaskScheduleInput(bId, "B", null, null, null, 5);
        TaskScheduleInput taskC = new TaskScheduleInput(cId, "C", null, null, null, 5);
        TaskScheduleInput taskD = new TaskScheduleInput(dId, "D", null, null, null, 4);

        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(aId, bId);
        graph.addEdge(aId, cId);
        graph.addEdge(bId, dId);
        graph.addEdge(cId, dId);

        CriticalPathResult r1 = calculator.calculate(List.of(taskA, taskB, taskC, taskD), graph);
        CriticalPathResult r2 = calculator.calculate(List.of(taskD, taskC, taskB, taskA), graph);

        assertThat(r1.criticalPaths()).isEqualTo(r2.criticalPaths());
        assertThat(r1.criticalTaskIds()).isEqualTo(r2.criticalTaskIds());
        assertThat(r1.taskMetrics()).isEqualTo(r2.taskMetrics());
    }
}
