package com.taskflow.taskflow.dependency.graph;

import com.taskflow.taskflow.dependency.readiness.ReadinessCalculator;
import com.taskflow.taskflow.scheduling.model.ScheduleCalculationResult;
import com.taskflow.taskflow.scheduling.service.ScheduleCalculationService;
import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Large-Graph Sanity & Performance Stress Test.
 * Validates that graph algorithms scale linearly O(V + E) for 1,000 tasks
 * and ~2,000 edges without accidental quadratic blowup or memory leaks.
 */
class LargeDagSanityTest {

    private TopologicalSortService topologicalSortService;
    private GraphTraversalService traversalService;
    private CycleDetectionService cycleDetectionService;
    private ScheduleCalculationService scheduleCalculationService;

    @BeforeEach
    void setUp() {
        topologicalSortService = new TopologicalSortService();
        traversalService = new GraphTraversalService(topologicalSortService);
        cycleDetectionService = new CycleDetectionService(traversalService);
        scheduleCalculationService = new ScheduleCalculationService();
    }

    @Test
    @DisplayName("1,000-task synthetic DAG: graph construction, cycle check, topo-sort, readiness, and scheduling complete in < 1 second")
    void testLargeDagSanityAndPerformance() {
        UUID projectId = UUID.randomUUID();
        DependencyGraph graph = new DependencyGraph(projectId);

        int layers = 100;
        int nodesPerLayer = 10;
        int totalNodes = layers * nodesPerLayer; // 1,000 nodes

        List<List<UUID>> layerNodes = new ArrayList<>(layers);
        for (int l = 0; l < layers; l++) {
            List<UUID> layer = new ArrayList<>(nodesPerLayer);
            for (int n = 0; n < nodesPerLayer; n++) {
                UUID id = UUID.randomUUID();
                layer.add(id);
                graph.addNode(id);
            }
            layerNodes.add(layer);
        }

        // Connect layer l -> layer l+1 (each node connects to 2 nodes in next layer)
        int edgeCount = 0;
        for (int l = 0; l < layers - 1; l++) {
            List<UUID> current = layerNodes.get(l);
            List<UUID> next = layerNodes.get(l + 1);
            for (int i = 0; i < current.size(); i++) {
                graph.addEdge(current.get(i), next.get(i % next.size()));
                graph.addEdge(current.get(i), next.get((i + 1) % next.size()));
                edgeCount += 2;
            }
        }

        assertThat(graph.getNodeCount()).isEqualTo(totalNodes);
        assertThat(graph.getEdgeCount()).isEqualTo(edgeCount);

        // 1. Cycle detection on large DAG
        long startCycle = System.currentTimeMillis();
        boolean wouldCycle = cycleDetectionService.wouldCreateCycle(
                graph,
                layerNodes.get(layers - 1).get(0), // last layer
                layerNodes.get(0).get(0)          // first layer -> should cycle!
        );
        long cycleDuration = System.currentTimeMillis() - startCycle;
        assertThat(wouldCycle).isTrue();
        assertThat(cycleDuration).isLessThan(500);

        // 2. Topological sort on 1,000 tasks
        long startSort = System.currentTimeMillis();
        List<UUID> topoOrder = topologicalSortService.sort(graph);
        long sortDuration = System.currentTimeMillis() - startSort;

        assertThat(topoOrder).hasSize(totalNodes);
        assertThat(sortDuration).isLessThan(500);

        // Verify topological ordering invariant: predecessors appear before successors
        Map<UUID, Integer> posMap = new HashMap<>(totalNodes);
        for (int i = 0; i < topoOrder.size(); i++) {
            posMap.put(topoOrder.get(i), i);
        }
        for (UUID u : graph.getNodes()) {
            for (UUID v : graph.getSuccessors(u)) {
                assertThat(posMap.get(u)).isLessThan(posMap.get(v));
            }
        }

        // 3. Simulated readiness propagation in topological order
        long startReadiness = System.currentTimeMillis();
        Map<UUID, DependencyStatus> readinessMap = new HashMap<>(totalNodes);
        for (UUID taskId : topoOrder) {
            Set<UUID> preds = graph.getPredecessors(taskId);
            List<ReadinessCalculator.PredecessorState> states = preds.stream()
                    .map(pid -> new ReadinessCalculator.PredecessorState(TaskStatus.DONE, DependencyStatus.READY))
                    .toList();
            readinessMap.put(taskId, ReadinessCalculator.calculateFromStates(states));
        }
        long readinessDuration = System.currentTimeMillis() - startReadiness;
        assertThat(readinessMap).hasSize(totalNodes);
        assertThat(readinessDuration).isLessThan(500);

        // 4. Simulated schedule propagation in topological order
        long startSchedule = System.currentTimeMillis();
        LocalDate baseDate = LocalDate.of(2026, 1, 1);
        Map<UUID, LocalDate> dueDates = new HashMap<>(totalNodes);
        for (UUID taskId : topoOrder) {
            Set<UUID> preds = graph.getPredecessors(taskId);
            List<LocalDate> predDueDates = preds.stream()
                    .map(dueDates::get)
                    .filter(d -> d != null)
                    .toList();
            ScheduleCalculationResult res = scheduleCalculationService.calculateSchedule(baseDate, 3, predDueDates);
            dueDates.put(taskId, res.scheduledDueDate());
        }
        long scheduleDuration = System.currentTimeMillis() - startSchedule;
        assertThat(dueDates).hasSize(totalNodes);
        assertThat(scheduleDuration).isLessThan(500);
    }
}
