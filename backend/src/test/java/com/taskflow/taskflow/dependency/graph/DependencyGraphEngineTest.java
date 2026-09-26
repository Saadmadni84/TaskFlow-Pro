package com.taskflow.taskflow.dependency.graph;

import com.taskflow.taskflow.common.exception.CycleDetectedException;
import com.taskflow.taskflow.common.exception.SelfDependencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyGraphEngineTest {

    private TopologicalSortService topologicalSortService;
    private GraphTraversalService traversalService;
    private CycleDetectionService cycleDetectionService;

    private UUID taskA;
    private UUID taskB;
    private UUID taskC;
    private UUID taskD;
    private UUID taskE;

    @BeforeEach
    void setUp() {
        topologicalSortService = new TopologicalSortService();
        traversalService = new GraphTraversalService(topologicalSortService);
        cycleDetectionService = new CycleDetectionService(traversalService);

        taskA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        taskB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        taskC = UUID.fromString("00000000-0000-0000-0000-000000000003");
        taskD = UUID.fromString("00000000-0000-0000-0000-000000000004");
        taskE = UUID.fromString("00000000-0000-0000-0000-000000000005");
    }

    /**
     * Property-style invariant verification:
     * For every edge U -> V in graph, position(U) < position(V) in the topological order.
     */
    private void assertValidTopologicalOrder(DependencyGraph graph, List<UUID> order) {
        assertThat(order).hasSize(graph.getNodeCount());
        assertThat(order).doesNotHaveDuplicates();

        Map<UUID, Integer> positions = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            positions.put(order.get(i), i);
        }

        for (UUID u : graph.getNodes()) {
            for (UUID v : graph.getSuccessors(u)) {
                int posU = positions.get(u);
                int posV = positions.get(v);
                assertThat(posU)
                        .withFailMessage("Invariant violated for edge %s -> %s: predecessor pos %d >= successor pos %d", u, v, posU, posV)
                        .isLessThan(posV);
            }
        }
    }

    @Test
    @DisplayName("Test 1: Empty graph")
    void test1_emptyGraph() {
        DependencyGraph graph = new DependencyGraph();

        List<UUID> order = topologicalSortService.sort(graph);

        assertThat(order).isEmpty();
        assertThat(cycleDetectionService.hasCycle(graph)).isFalse();
    }

    @Test
    @DisplayName("Test 2: Single node without dependencies")
    void test2_singleNode() {
        DependencyGraph graph = new DependencyGraph();
        graph.addNode(taskA);

        List<UUID> order = topologicalSortService.sort(graph);

        assertThat(order).containsExactly(taskA);
        assertThat(cycleDetectionService.hasCycle(graph)).isFalse();
        assertValidTopologicalOrder(graph, order);
    }

    @Test
    @DisplayName("Test 3: Linear graph A -> B -> C")
    void test3_linearGraph() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskB, taskC);

        List<UUID> order = topologicalSortService.sort(graph);

        assertThat(order).containsExactly(taskA, taskB, taskC);
        assertThat(cycleDetectionService.hasCycle(graph)).isFalse();
        assertValidTopologicalOrder(graph, order);
    }

    @Test
    @DisplayName("Test 4: Branching graph A -> B and A -> C")
    void test4_branchingGraph() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskA, taskC);

        List<UUID> order = topologicalSortService.sort(graph);

        // A must be first, B and C deterministic tie-break
        assertThat(order.get(0)).isEqualTo(taskA);
        assertThat(order).containsExactlyInAnyOrder(taskA, taskB, taskC);
        assertValidTopologicalOrder(graph, order);
    }

    @Test
    @DisplayName("Test 5: Converging graph A -> B -> D and A -> C -> D")
    void test5_convergingGraph() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskA, taskC);
        graph.addEdge(taskB, taskD);
        graph.addEdge(taskC, taskD);

        List<UUID> order = topologicalSortService.sort(graph);

        assertThat(order.get(0)).isEqualTo(taskA);
        assertThat(order.get(3)).isEqualTo(taskD);
        assertValidTopologicalOrder(graph, order);
    }

    @Test
    @DisplayName("Test 6: Multiple roots A -> C and B -> C")
    void test6_multipleRoots() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskC);
        graph.addEdge(taskB, taskC);

        List<UUID> order = topologicalSortService.sort(graph);

        // A and B must precede C
        assertThat(order.indexOf(taskA)).isLessThan(order.indexOf(taskC));
        assertThat(order.indexOf(taskB)).isLessThan(order.indexOf(taskC));
        assertValidTopologicalOrder(graph, order);
    }

    @Test
    @DisplayName("Test 7: Self dependency A -> A rejected immediately")
    void test7_selfDependency() {
        DependencyGraph graph = new DependencyGraph();
        graph.addNode(taskA);

        assertThatThrownBy(() -> graph.addEdge(taskA, taskA))
                .isInstanceOf(SelfDependencyException.class)
                .hasMessageContaining("Self-dependency is forbidden");

        assertThat(cycleDetectionService.wouldCreateCycle(graph, taskA, taskA)).isTrue();
    }

    @Test
    @DisplayName("Test 8: Direct cycle A -> B and B -> A detected")
    void test8_directCycle() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);

        // Before adding B -> A, cycle detection must predict cycle
        assertThat(cycleDetectionService.wouldCreateCycle(graph, taskB, taskA)).isTrue();

        Optional<List<UUID>> path = cycleDetectionService.getPotentialCyclePath(graph, taskB, taskA);
        assertThat(path).isPresent();
        assertThat(path.get()).containsExactly(taskB, taskA, taskB);

        // If forced into graph, sort must detect cycle
        graph.addEdge(taskB, taskA);
        assertThat(cycleDetectionService.hasCycle(graph)).isTrue();
        assertThatThrownBy(() -> topologicalSortService.sort(graph))
                .isInstanceOf(CycleDetectedException.class);
    }

    @Test
    @DisplayName("Test 9: Indirect cycle A -> B -> C -> A detected")
    void test9_indirectCycle() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskB, taskC);

        assertThat(cycleDetectionService.wouldCreateCycle(graph, taskC, taskA)).isTrue();

        graph.addEdge(taskC, taskA);
        assertThat(cycleDetectionService.hasCycle(graph)).isTrue();

        Optional<List<UUID>> cycle = cycleDetectionService.findCycle(graph);
        assertThat(cycle).isPresent();

        assertThatThrownBy(() -> topologicalSortService.sort(graph))
                .isInstanceOf(CycleDetectedException.class);
    }

    @Test
    @DisplayName("Test 10: Longer cycle A -> B -> C -> D -> E -> A detected")
    void test10_longerCycle() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskB, taskC);
        graph.addEdge(taskC, taskD);
        graph.addEdge(taskD, taskE);

        assertThat(cycleDetectionService.wouldCreateCycle(graph, taskE, taskA)).isTrue();

        graph.addEdge(taskE, taskA);
        assertThat(cycleDetectionService.hasCycle(graph)).isTrue();
        assertThatThrownBy(() -> topologicalSortService.sort(graph))
                .isInstanceOf(CycleDetectedException.class);
    }

    @Test
    @DisplayName("Test 11: Duplicate edge A -> B handled idempotently")
    void test11_duplicateEdge() {
        DependencyGraph graph = new DependencyGraph();
        boolean firstAdd = graph.addEdge(taskA, taskB);
        boolean secondAdd = graph.addEdge(taskA, taskB);

        assertThat(firstAdd).isTrue();
        assertThat(secondAdd).isFalse();
        assertThat(graph.getEdgeCount()).isEqualTo(1);
        assertThat(graph.getSuccessors(taskA)).hasSize(1);
    }

    @Test
    @DisplayName("Test 12: Reachability validation")
    void test12_reachability() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskB, taskC);
        graph.addEdge(taskA, taskD);

        assertThat(traversalService.isReachable(graph, taskA, taskC)).isTrue();
        assertThat(traversalService.isReachable(graph, taskA, taskD)).isTrue();
        assertThat(traversalService.isReachable(graph, taskB, taskC)).isTrue();
        assertThat(traversalService.isReachable(graph, taskC, taskA)).isFalse();
        assertThat(traversalService.isReachable(graph, taskB, taskD)).isFalse();
        assertThat(traversalService.isReachable(graph, taskD, taskC)).isFalse();
    }

    @Test
    @DisplayName("Test 13: Descendants traversal")
    void test13_descendants() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskB, taskC);
        graph.addEdge(taskA, taskD);

        assertThat(traversalService.getDescendants(graph, taskA))
                .containsExactlyInAnyOrder(taskB, taskC, taskD);

        assertThat(traversalService.getDescendants(graph, taskB))
                .containsExactly(taskC);

        assertThat(traversalService.getDescendants(graph, taskC)).isEmpty();
    }

    @Test
    @DisplayName("Test 14: Ancestors traversal")
    void test14_ancestors() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskB, taskC);
        graph.addEdge(taskA, taskD);

        assertThat(traversalService.getAncestors(graph, taskC))
                .containsExactlyInAnyOrder(taskA, taskB);

        assertThat(traversalService.getAncestors(graph, taskD))
                .containsExactly(taskA);

        assertThat(traversalService.getAncestors(graph, taskA)).isEmpty();
    }

    @Test
    @DisplayName("Test 15: Converging descendants contains node D exactly once without duplicates")
    void test15_convergingDescendantsNoDuplicates() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskA, taskC);
        graph.addEdge(taskB, taskD);
        graph.addEdge(taskC, taskD);

        Set<UUID> descendantsA = traversalService.getDescendants(graph, taskA);

        assertThat(descendantsA).containsExactlyInAnyOrder(taskB, taskC, taskD);
        assertThat(descendantsA).hasSize(3);
    }

    @Test
    @DisplayName("Test 16: Proposed dependency cycle check keeps graph unchanged on rejection")
    void test16_proposedDependencyCycleCheck() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskB, taskC);

        boolean wouldCycle = cycleDetectionService.wouldCreateCycle(graph, taskC, taskA);
        assertThat(wouldCycle).isTrue();

        // Verify graph remains completely unchanged
        assertThat(graph.containsEdge(taskC, taskA)).isFalse();
        assertThat(graph.getEdgeCount()).isEqualTo(2);
        assertThat(cycleDetectionService.hasCycle(graph)).isFalse();
    }

    @Test
    @DisplayName("Test 17: Affected subgraph produces topologically ordered descendants")
    void test17_affectedSubgraphTopologicalOrder() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(taskA, taskB);
        graph.addEdge(taskA, taskC);
        graph.addEdge(taskB, taskD);
        graph.addEdge(taskC, taskD);

        AffectedSubgraph subgraph = traversalService.getAffectedSubgraph(graph, taskA);

        assertThat(subgraph.rootTaskId()).isEqualTo(taskA);
        assertThat(subgraph.affectedTaskIds()).containsExactlyInAnyOrder(taskB, taskC, taskD);

        List<UUID> order = subgraph.topologicalOrder();
        assertThat(order).containsExactlyInAnyOrder(taskB, taskC, taskD);

        // B and C must appear before D
        assertThat(order.indexOf(taskB)).isLessThan(order.indexOf(taskD));
        assertThat(order.indexOf(taskC)).isLessThan(order.indexOf(taskD));
    }
}
