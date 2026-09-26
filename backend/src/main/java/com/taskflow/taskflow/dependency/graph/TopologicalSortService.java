package com.taskflow.taskflow.dependency.graph;

import com.taskflow.taskflow.common.exception.CycleDetectedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic topological sorting service implementing Kahn's algorithm.
 *
 * Guarantees:
 * 1. Predecessors always appear before their successors.
 * 2. Determinism: When multiple nodes are eligible (in-degree 0), ties are broken
 *    using natural UUID lexical ordering so output is 100% reproducible.
 * 3. Cycle safety: If processed count < total nodes, CycleDetectedException is thrown.
 *
 * Algorithm complexity: O(V + E)
 */
@Service
public class TopologicalSortService {

    private static final Comparator<UUID> DETERMINISTIC_TIE_BREAKER = Comparator.naturalOrder();

    /**
     * Computes the deterministic topological order for all nodes in the graph.
     * Throws CycleDetectedException if the graph contains any cycle.
     */
    public List<UUID> sort(DependencyGraph graph) {
        if (graph == null || graph.getNodeCount() == 0) {
            return Collections.emptyList();
        }

        return sortNodes(graph, graph.getNodes());
    }

    /**
     * Computes the deterministic topological order for a specific subset of nodes
     * considering only edges between nodes within the subset.
     */
    public List<UUID> sortSubset(DependencyGraph graph, Set<UUID> subset) {
        if (graph == null || subset == null || subset.isEmpty()) {
            return Collections.emptyList();
        }

        return sortNodes(graph, subset);
    }

    private List<UUID> sortNodes(DependencyGraph graph, Set<UUID> targetNodes) {
        Map<UUID, Integer> inDegreeMap = new HashMap<>();
        Set<UUID> nodeSet = new HashSet<>(targetNodes);

        // Calculate in-degree for target nodes restricted to dependencies within targetNodes
        for (UUID node : nodeSet) {
            int inDegree = 0;
            for (UUID predecessor : graph.getPredecessors(node)) {
                if (nodeSet.contains(predecessor)) {
                    inDegree++;
                }
            }
            inDegreeMap.put(node, inDegree);
        }

        // PriorityQueue enforces deterministic tie-breaking on identical in-degree (0)
        PriorityQueue<UUID> queue = new PriorityQueue<>(DETERMINISTIC_TIE_BREAKER);
        for (UUID node : nodeSet) {
            if (inDegreeMap.get(node) == 0) {
                queue.add(node);
            }
        }

        List<UUID> topologicalOrder = new ArrayList<>(nodeSet.size());

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            topologicalOrder.add(current);

            for (UUID successor : graph.getSuccessors(current)) {
                if (nodeSet.contains(successor)) {
                    int remainingInDegree = inDegreeMap.get(successor) - 1;
                    inDegreeMap.put(successor, remainingInDegree);
                    if (remainingInDegree == 0) {
                        queue.add(successor);
                    }
                }
            }
        }

        if (topologicalOrder.size() < nodeSet.size()) {
            throw new CycleDetectedException("Graph contains a cycle; topological sort cannot be completed");
        }

        return Collections.unmodifiableList(topologicalOrder);
    }
}
