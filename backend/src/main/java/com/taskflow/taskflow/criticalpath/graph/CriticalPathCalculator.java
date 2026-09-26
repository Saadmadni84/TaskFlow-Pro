package com.taskflow.taskflow.criticalpath.graph;

import com.taskflow.taskflow.common.exception.CycleDetectedException;
import com.taskflow.taskflow.criticalpath.model.CriticalPathResult;
import com.taskflow.taskflow.criticalpath.model.TaskScheduleInput;
import com.taskflow.taskflow.criticalpath.model.TaskScheduleMetrics;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure calculation engine for Critical Path Analysis implementing the Critical Path Method (CPM).
 *
 * Guarantees:
 * 1. Independent of JPA, Spring HTTP, and database persistence.
 * 2. Inclusive calendar date arithmetic matching Phase 5:
 *    - dueDate = startDate + durationDays - 1 day
 *    - successor constraint: successor.ES >= predecessor.EF + 1 day
 * 3. Topological sorting with deterministic UUID tie-breaking.
 * 4. Cycle detection safety: throws CycleDetectedException if graph has a cycle.
 * 5. Bounded critical path reconstruction (avoids exponential path explosion).
 * 6. Supports multiple roots, multiple terminal branches, and multiple critical paths.
 * 7. O(V + E) time complexity for forward pass, backward pass, and slack computation.
 */
@Component
public class CriticalPathCalculator {

    public static final int MAX_CRITICAL_PATHS = 100;
    private static final Comparator<UUID> DETERMINISTIC_TIE_BREAKER = Comparator.naturalOrder();

    /**
     * Executes Critical Path Analysis on the provided tasks and dependency graph.
     *
     * @param tasks collection of task schedule inputs
     * @param graph directed dependency graph where edges represent predecessor -> successor
     * @return calculated CriticalPathResult
     */
    public CriticalPathResult calculate(Collection<TaskScheduleInput> tasks, DependencyGraph graph) {
        if (tasks == null || tasks.isEmpty()) {
            return CriticalPathResult.empty();
        }

        // 1. Index tasks and populate graph nodes
        Map<UUID, TaskScheduleInput> taskMap = new HashMap<>();
        DependencyGraph effectiveGraph = graph != null ? graph : new DependencyGraph();

        for (TaskScheduleInput task : tasks) {
            taskMap.put(task.taskId(), task);
            effectiveGraph.addNode(task.taskId());
        }

        Set<UUID> allTaskIds = taskMap.keySet();

        // 2. Perform Kahn's Topological Sort with deterministic tie-breaking (Cycle Safety)
        List<UUID> topologicalOrder = computeTopologicalOrder(effectiveGraph, allTaskIds);

        // 3. Determine Project Start Date deterministically
        LocalDate projectStartDate = determineProjectStartDate(topologicalOrder, effectiveGraph, taskMap);

        // 4. Forward Pass (Topological Order) -> Earliest Start (ES) and Earliest Finish (EF)
        Map<UUID, LocalDate> earliestStartMap = new HashMap<>();
        Map<UUID, LocalDate> earliestFinishMap = new HashMap<>();

        for (UUID taskId : topologicalOrder) {
            TaskScheduleInput input = taskMap.get(taskId);
            Set<UUID> predecessors = effectiveGraph.getPredecessors(taskId).stream()
                    .filter(allTaskIds::contains)
                    .collect(Collectors.toSet());

            LocalDate es;
            if (predecessors.isEmpty()) {
                // Root task
                LocalDate base = input.getEffectiveBaseStartDate();
                es = (base != null && base.isAfter(projectStartDate)) ? base : projectStartDate;
            } else {
                LocalDate maxPredConstraint = null;
                for (UUID predId : predecessors) {
                    LocalDate predEF = earliestFinishMap.get(predId);
                    LocalDate constraint = predEF.plusDays(1);
                    if (maxPredConstraint == null || constraint.isAfter(maxPredConstraint)) {
                        maxPredConstraint = constraint;
                    }
                }
                LocalDate base = input.getEffectiveBaseStartDate();
                es = (base != null && base.isAfter(maxPredConstraint)) ? base : maxPredConstraint;
            }

            LocalDate ef = es.plusDays(input.durationDays() - 1);
            earliestStartMap.put(taskId, es);
            earliestFinishMap.put(taskId, ef);
        }

        // 5. Terminal Tasks & Project Completion Date
        List<UUID> terminalTasks = topologicalOrder.stream()
                .filter(id -> effectiveGraph.getSuccessors(id).stream().noneMatch(allTaskIds::contains))
                .toList();

        LocalDate projectCompletionDate = terminalTasks.stream()
                .map(earliestFinishMap::get)
                .max(LocalDate::compareTo)
                .orElse(projectStartDate);

        // 6. Backward Pass (Reverse Topological Order) -> Latest Finish (LF) and Latest Start (LS)
        Map<UUID, LocalDate> latestFinishMap = new HashMap<>();
        Map<UUID, LocalDate> latestStartMap = new HashMap<>();

        for (int i = topologicalOrder.size() - 1; i >= 0; i--) {
            UUID taskId = topologicalOrder.get(i);
            TaskScheduleInput input = taskMap.get(taskId);
            Set<UUID> successors = effectiveGraph.getSuccessors(taskId).stream()
                    .filter(allTaskIds::contains)
                    .collect(Collectors.toSet());

            LocalDate lf;
            if (successors.isEmpty()) {
                // Terminal task
                lf = projectCompletionDate;
            } else {
                LocalDate minSuccConstraint = null;
                for (UUID succId : successors) {
                    LocalDate succLS = latestStartMap.get(succId);
                    LocalDate constraint = succLS.minusDays(1);
                    if (minSuccConstraint == null || constraint.isBefore(minSuccConstraint)) {
                        minSuccConstraint = constraint;
                    }
                }
                lf = minSuccConstraint;
            }

            LocalDate ls = lf.minusDays(input.durationDays() - 1);
            latestFinishMap.put(taskId, lf);
            latestStartMap.put(taskId, ls);
        }

        // 7. Calculate Slack and Identify Critical Tasks (Slack == 0)
        List<TaskScheduleMetrics> metricsList = new ArrayList<>(topologicalOrder.size());
        List<UUID> criticalTaskIds = new ArrayList<>();
        Set<UUID> criticalTaskSet = new HashSet<>();

        for (UUID taskId : topologicalOrder) {
            TaskScheduleInput input = taskMap.get(taskId);
            LocalDate es = earliestStartMap.get(taskId);
            LocalDate ef = earliestFinishMap.get(taskId);
            LocalDate ls = latestStartMap.get(taskId);
            LocalDate lf = latestFinishMap.get(taskId);

            long totalSlackDays = ChronoUnit.DAYS.between(es, ls);
            boolean isCritical = (totalSlackDays == 0);

            if (isCritical) {
                criticalTaskIds.add(taskId);
                criticalTaskSet.add(taskId);
            }

            metricsList.add(new TaskScheduleMetrics(
                    taskId,
                    input.title(),
                    input.durationDays(),
                    es,
                    ef,
                    ls,
                    lf,
                    totalSlackDays,
                    isCritical
            ));
        }

        // 8. Reconstruct Critical Paths (Zero-Slack Subgraph Traversal)
        CriticalPathExtractionResult pathResult = extractCriticalPaths(
                criticalTaskSet,
                effectiveGraph,
                earliestStartMap,
                earliestFinishMap,
                projectCompletionDate
        );

        return new CriticalPathResult(
                projectStartDate,
                projectCompletionDate,
                criticalTaskIds,
                pathResult.criticalPaths(),
                metricsList,
                pathResult.isTruncated()
        );
    }

    private List<UUID> computeTopologicalOrder(DependencyGraph graph, Set<UUID> nodeSet) {
        Map<UUID, Integer> inDegreeMap = new HashMap<>();

        for (UUID node : nodeSet) {
            int inDegree = 0;
            for (UUID pred : graph.getPredecessors(node)) {
                if (nodeSet.contains(pred)) {
                    inDegree++;
                }
            }
            inDegreeMap.put(node, inDegree);
        }

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

            for (UUID succ : graph.getSuccessors(current)) {
                if (nodeSet.contains(succ)) {
                    int remaining = inDegreeMap.get(succ) - 1;
                    inDegreeMap.put(succ, remaining);
                    if (remaining == 0) {
                        queue.add(succ);
                    }
                }
            }
        }

        if (topologicalOrder.size() < nodeSet.size()) {
            throw new CycleDetectedException("Graph contains a cycle; critical path analysis cannot be completed");
        }

        return topologicalOrder;
    }

    private LocalDate determineProjectStartDate(
            List<UUID> topologicalOrder,
            DependencyGraph graph,
            Map<UUID, TaskScheduleInput> taskMap
    ) {
        Set<UUID> allTaskIds = taskMap.keySet();
        List<UUID> rootTasks = topologicalOrder.stream()
                .filter(id -> graph.getPredecessors(id).stream().noneMatch(allTaskIds::contains))
                .toList();

        // 1. Min scheduled/planned start among root tasks
        LocalDate minRootStart = rootTasks.stream()
                .map(id -> taskMap.get(id).getEffectiveBaseStartDate())
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);

        if (minRootStart != null) {
            return minRootStart;
        }

        // 2. Fall back to min scheduled/planned start across all tasks
        return taskMap.values().stream()
                .map(TaskScheduleInput::getEffectiveBaseStartDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
    }

    private CriticalPathExtractionResult extractCriticalPaths(
            Set<UUID> criticalTaskSet,
            DependencyGraph graph,
            Map<UUID, LocalDate> earliestStartMap,
            Map<UUID, LocalDate> earliestFinishMap,
            LocalDate projectCompletionDate
    ) {
        if (criticalTaskSet.isEmpty()) {
            return new CriticalPathExtractionResult(Collections.emptyList(), false);
        }

        // Build tight/binding critical edges: u -> v where v.ES == u.EF + 1 day
        Map<UUID, List<UUID>> criticalSuccessors = new HashMap<>();
        Map<UUID, Integer> criticalInDegree = new HashMap<>();

        for (UUID cId : criticalTaskSet) {
            criticalSuccessors.put(cId, new ArrayList<>());
            criticalInDegree.put(cId, 0);
        }

        for (UUID u : criticalTaskSet) {
            LocalDate uEF = earliestFinishMap.get(u);
            for (UUID v : graph.getSuccessors(u)) {
                if (criticalTaskSet.contains(v)) {
                    LocalDate vES = earliestStartMap.get(v);
                    if (vES.equals(uEF.plusDays(1))) {
                        criticalSuccessors.get(u).add(v);
                        criticalInDegree.put(v, criticalInDegree.get(v) + 1);
                    }
                }
            }
        }

        // Critical roots: critical tasks with no incoming critical predecessor
        List<UUID> criticalRoots = criticalTaskSet.stream()
                .filter(u -> criticalInDegree.get(u) == 0)
                .sorted(DETERMINISTIC_TIE_BREAKER)
                .toList();

        // Critical terminals: critical tasks with no critical successors that finish on projectCompletionDate
        Set<UUID> criticalTerminals = criticalTaskSet.stream()
                .filter(u -> earliestFinishMap.get(u).equals(projectCompletionDate) && criticalSuccessors.get(u).isEmpty())
                .collect(Collectors.toSet());

        List<List<UUID>> allCriticalPaths = new ArrayList<>();
        boolean[] isTruncated = new boolean[]{false};

        for (UUID root : criticalRoots) {
            if (allCriticalPaths.size() >= MAX_CRITICAL_PATHS) {
                isTruncated[0] = true;
                break;
            }
            List<UUID> currentPath = new ArrayList<>();
            findPathsDfs(root, criticalSuccessors, criticalTerminals, currentPath, allCriticalPaths, MAX_CRITICAL_PATHS, isTruncated);
        }

        return new CriticalPathExtractionResult(allCriticalPaths, isTruncated[0]);
    }

    private void findPathsDfs(
            UUID node,
            Map<UUID, List<UUID>> criticalSuccessors,
            Set<UUID> criticalTerminals,
            List<UUID> currentPath,
            List<List<UUID>> allPaths,
            int maxPaths,
            boolean[] isTruncated
    ) {
        currentPath.add(node);

        if (criticalTerminals.contains(node) && criticalSuccessors.get(node).isEmpty()) {
            allPaths.add(new ArrayList<>(currentPath));
            if (allPaths.size() >= maxPaths) {
                isTruncated[0] = true;
            }
        } else {
            List<UUID> succs = criticalSuccessors.get(node).stream()
                    .sorted(DETERMINISTIC_TIE_BREAKER)
                    .toList();

            for (UUID succ : succs) {
                if (allPaths.size() >= maxPaths) {
                    isTruncated[0] = true;
                    break;
                }
                findPathsDfs(succ, criticalSuccessors, criticalTerminals, currentPath, allPaths, maxPaths, isTruncated);
            }
        }

        currentPath.remove(currentPath.size() - 1);
    }

    private record CriticalPathExtractionResult(
            List<List<UUID>> criticalPaths,
            boolean isTruncated
    ) {}
}
