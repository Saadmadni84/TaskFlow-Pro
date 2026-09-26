package com.taskflow.taskflow.dependency.graph;

import com.taskflow.taskflow.common.exception.SelfDependencyException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure in-memory directed graph representing task dependencies for a project.
 *
 * Each task is a node. Each dependency is a directed edge:
 * predecessor -> successor
 *
 * Incoming edges (predecessors): tasks that must finish before this task can start.
 * Outgoing edges (successors): tasks waiting on this task to finish.
 */
public class DependencyGraph {

    private final UUID projectId;
    private final Set<UUID> nodes;
    private final Map<UUID, Set<UUID>> successors;
    private final Map<UUID, Set<UUID>> predecessors;
    private int edgeCount;

    public DependencyGraph() {
        this(null);
    }

    public DependencyGraph(UUID projectId) {
        this.projectId = projectId;
        this.nodes = new LinkedHashSet<>();
        this.successors = new HashMap<>();
        this.predecessors = new HashMap<>();
        this.edgeCount = 0;
    }

    public UUID getProjectId() {
        return projectId;
    }

    /**
     * Adds a task node to the graph if absent.
     */
    public boolean addNode(UUID taskId) {
        Objects.requireNonNull(taskId, "Task ID must not be null");
        if (nodes.add(taskId)) {
            successors.putIfAbsent(taskId, new HashSet<>());
            predecessors.putIfAbsent(taskId, new HashSet<>());
            return true;
        }
        return false;
    }

    /**
     * Adds a directed dependency edge: predecessor -> successor.
     * Rejects self-dependencies. Idempotent for duplicate edges.
     */
    public boolean addEdge(UUID predecessor, UUID successor) {
        Objects.requireNonNull(predecessor, "Predecessor task ID must not be null");
        Objects.requireNonNull(successor, "Successor task ID must not be null");

        if (predecessor.equals(successor)) {
            throw new SelfDependencyException(predecessor);
        }

        addNode(predecessor);
        addNode(successor);

        Set<UUID> succSet = successors.get(predecessor);
        if (succSet.add(successor)) {
            predecessors.get(successor).add(predecessor);
            edgeCount++;
            return true;
        }
        return false;
    }

    /**
     * Removes a directed dependency edge: predecessor -> successor.
     */
    public boolean removeEdge(UUID predecessor, UUID successor) {
        if (predecessor == null || successor == null) {
            return false;
        }
        Set<UUID> succSet = successors.get(predecessor);
        if (succSet != null && succSet.remove(successor)) {
            Set<UUID> predSet = predecessors.get(successor);
            if (predSet != null) {
                predSet.remove(predecessor);
            }
            edgeCount--;
            return true;
        }
        return false;
    }

    public boolean containsNode(UUID taskId) {
        return taskId != null && nodes.contains(taskId);
    }

    public boolean containsEdge(UUID predecessor, UUID successor) {
        if (predecessor == null || successor == null) {
            return false;
        }
        Set<UUID> succs = successors.get(predecessor);
        return succs != null && succs.contains(successor);
    }

    public Set<UUID> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    public Set<UUID> getSuccessors(UUID taskId) {
        Set<UUID> succs = successors.get(taskId);
        return succs != null ? Collections.unmodifiableSet(succs) : Collections.emptySet();
    }

    public Set<UUID> getPredecessors(UUID taskId) {
        Set<UUID> preds = predecessors.get(taskId);
        return preds != null ? Collections.unmodifiableSet(preds) : Collections.emptySet();
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public int getInDegree(UUID taskId) {
        Set<UUID> preds = predecessors.get(taskId);
        return preds != null ? preds.size() : 0;
    }

    public int getOutDegree(UUID taskId) {
        Set<UUID> succs = successors.get(taskId);
        return succs != null ? succs.size() : 0;
    }
}
