package com.taskflow.taskflow.dependency.graph;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic cycle detection service implementing DFS cycle detection
 * and targeted reachability validation for proposed dependency edges.
 *
 * Algorithm complexity: O(V + E)
 */
@Service
public class CycleDetectionService {

    private enum NodeState {
        UNVISITED,
        VISITING,
        VISITED
    }

    private final GraphTraversalService traversalService;

    public CycleDetectionService(GraphTraversalService traversalService) {
        this.traversalService = traversalService;
    }

    /**
     * Checks if the dependency graph contains any cycle.
     */
    public boolean hasCycle(DependencyGraph graph) {
        return findCycle(graph).isPresent();
    }

    /**
     * Performs a full DFS cycle detection traversal over the graph.
     * If a cycle is detected, returns the ordered list of task IDs forming the cycle.
     */
    public Optional<List<UUID>> findCycle(DependencyGraph graph) {
        if (graph == null || graph.getNodeCount() == 0) {
            return Optional.empty();
        }

        Map<UUID, NodeState> states = new HashMap<>();
        Map<UUID, UUID> parentMap = new HashMap<>();

        for (UUID node : graph.getNodes()) {
            states.put(node, NodeState.UNVISITED);
        }

        for (UUID node : graph.getNodes()) {
            if (states.get(node) == NodeState.UNVISITED) {
                Optional<List<UUID>> cycle = dfsCycle(node, graph, states, parentMap);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<List<UUID>> dfsCycle(
            UUID current,
            DependencyGraph graph,
            Map<UUID, NodeState> states,
            Map<UUID, UUID> parentMap
    ) {
        states.put(current, NodeState.VISITING);

        for (UUID successor : graph.getSuccessors(current)) {
            NodeState successorState = states.get(successor);

            // A back-edge to a node currently in the DFS recursion stack proves a cycle
            if (successorState == NodeState.VISITING) {
                List<UUID> cycle = new ArrayList<>();
                cycle.add(successor);
                UUID curr = current;
                while (curr != null && !curr.equals(successor)) {
                    cycle.add(curr);
                    curr = parentMap.get(curr);
                }
                cycle.add(successor);
                Collections.reverse(cycle);
                return Optional.of(cycle);
            }

            if (successorState == NodeState.UNVISITED) {
                parentMap.put(successor, current);
                Optional<List<UUID>> cycle = dfsCycle(successor, graph, states, parentMap);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }

        states.put(current, NodeState.VISITED);
        return Optional.empty();
    }

    /**
     * Efficiently tests whether adding a proposed directed edge:
     * predecessor -> successor
     * would create a cycle in the existing graph, WITHOUT mutating the graph.
     *
     * Invariant: Adding A -> B creates a cycle if and only if B can already reach A (B -> ... -> A).
     */
    public boolean wouldCreateCycle(DependencyGraph graph, UUID proposedPredecessor, UUID proposedSuccessor) {
        Objects.requireNonNull(proposedPredecessor, "Proposed predecessor must not be null");
        Objects.requireNonNull(proposedSuccessor, "Proposed successor must not be null");

        // Self-dependency is an immediate 1-hop cycle
        if (proposedPredecessor.equals(proposedSuccessor)) {
            return true;
        }

        if (graph == null || !graph.containsNode(proposedSuccessor) || !graph.containsNode(proposedPredecessor)) {
            return false;
        }

        // If successor can already reach predecessor, adding predecessor -> successor closes a cycle
        return traversalService.isReachable(graph, proposedSuccessor, proposedPredecessor);
    }

    /**
     * If adding predecessor -> successor would create a cycle, returns the cycle path
     * [predecessor, successor, ..., predecessor].
     */
    public Optional<List<UUID>> getPotentialCyclePath(DependencyGraph graph, UUID proposedPredecessor, UUID proposedSuccessor) {
        if (proposedPredecessor.equals(proposedSuccessor)) {
            return Optional.of(List.of(proposedPredecessor, proposedPredecessor));
        }

        Optional<List<UUID>> pathToPredecessor = traversalService.findPath(graph, proposedSuccessor, proposedPredecessor);
        if (pathToPredecessor.isPresent()) {
            List<UUID> cycle = new ArrayList<>();
            cycle.add(proposedPredecessor);
            cycle.addAll(pathToPredecessor.get());
            return Optional.of(cycle);
        }

        return Optional.empty();
    }
}
