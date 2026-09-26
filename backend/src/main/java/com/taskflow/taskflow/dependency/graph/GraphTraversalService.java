package com.taskflow.taskflow.dependency.graph;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Pure graph traversal service executing BFS/DFS traversals for reachability,
 * descendant calculation, and ancestor calculation without database dependencies.
 */
@Service
public class GraphTraversalService {

    private final TopologicalSortService topologicalSortService;

    public GraphTraversalService(TopologicalSortService topologicalSortService) {
        this.topologicalSortService = topologicalSortService;
    }

    /**
     * Constructs the AffectedSubgraph for rootTaskId containing all descendants
     * ordered in valid topological sequence.
     */
    public AffectedSubgraph getAffectedSubgraph(DependencyGraph graph, UUID rootTaskId) {
        Set<UUID> descendants = getDescendants(graph, rootTaskId);
        List<UUID> topologicalOrder = topologicalSortService.sortSubset(graph, descendants);
        return new AffectedSubgraph(rootTaskId, descendants, topologicalOrder);
    }

    /**
     * Determines whether there is a directed path from source to target with length >= 1.
     * Complexity: O(V + E)
     */
    public boolean isReachable(DependencyGraph graph, UUID source, UUID target) {
        if (graph == null || source == null || target == null) {
            return false;
        }
        if (!graph.containsNode(source) || !graph.containsNode(target)) {
            return false;
        }

        Queue<UUID> queue = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();

        // Start search from direct successors of source (path length >= 1)
        for (UUID directSuccessor : graph.getSuccessors(source)) {
            if (directSuccessor.equals(target)) {
                return true;
            }
            if (visited.add(directSuccessor)) {
                queue.add(directSuccessor);
            }
        }

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (UUID next : graph.getSuccessors(current)) {
                if (next.equals(target)) {
                    return true;
                }
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        return false;
    }

    /**
     * Finds a directed path from source to target if one exists.
     * Returns Optional containing the path [source, ..., target], or empty if unreachable.
     */
    public Optional<List<UUID>> findPath(DependencyGraph graph, UUID source, UUID target) {
        if (graph == null || source == null || target == null) {
            return Optional.empty();
        }
        if (!graph.containsNode(source) || !graph.containsNode(target)) {
            return Optional.empty();
        }

        Queue<UUID> queue = new ArrayDeque<>();
        Map<UUID, UUID> parentMap = new HashMap<>();
        Set<UUID> visited = new HashSet<>();

        for (UUID directSuccessor : graph.getSuccessors(source)) {
            parentMap.put(directSuccessor, source);
            if (directSuccessor.equals(target)) {
                return Optional.of(List.of(source, target));
            }
            if (visited.add(directSuccessor)) {
                queue.add(directSuccessor);
            }
        }

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (UUID next : graph.getSuccessors(current)) {
                if (!parentMap.containsKey(next)) {
                    parentMap.put(next, current);
                    if (next.equals(target)) {
                        List<UUID> path = new ArrayList<>();
                        UUID curr = target;
                        while (curr != null && !curr.equals(source)) {
                            path.add(curr);
                            curr = parentMap.get(curr);
                        }
                        path.add(source);
                        Collections.reverse(path);
                        return Optional.of(path);
                    }
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Retrieves all downstream descendants of rootTaskId.
     * Guarantees no duplicates.
     * Complexity: O(V + E)
     */
    public Set<UUID> getDescendants(DependencyGraph graph, UUID rootTaskId) {
        if (graph == null || rootTaskId == null || !graph.containsNode(rootTaskId)) {
            return Collections.emptySet();
        }

        Set<UUID> descendants = new LinkedHashSet<>();
        Queue<UUID> queue = new ArrayDeque<>();

        for (UUID successor : graph.getSuccessors(rootTaskId)) {
            if (descendants.add(successor)) {
                queue.add(successor);
            }
        }

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (UUID next : graph.getSuccessors(current)) {
                if (descendants.add(next)) {
                    queue.add(next);
                }
            }
        }

        return Collections.unmodifiableSet(descendants);
    }

    /**
     * Retrieves all upstream ancestors of rootTaskId.
     * Guarantees no duplicates.
     * Complexity: O(V + E)
     */
    public Set<UUID> getAncestors(DependencyGraph graph, UUID rootTaskId) {
        if (graph == null || rootTaskId == null || !graph.containsNode(rootTaskId)) {
            return Collections.emptySet();
        }

        Set<UUID> ancestors = new LinkedHashSet<>();
        Queue<UUID> queue = new ArrayDeque<>();

        for (UUID predecessor : graph.getPredecessors(rootTaskId)) {
            if (ancestors.add(predecessor)) {
                queue.add(predecessor);
            }
        }

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (UUID next : graph.getPredecessors(current)) {
                if (ancestors.add(next)) {
                    queue.add(next);
                }
            }
        }

        return Collections.unmodifiableSet(ancestors);
    }
}
