'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import dagre from '@dagrejs/dagre';
import { Node, Edge, MarkerType, Position } from '@xyflow/react';
import { DependencyGraph, DependencyGraphNode, DependencyGraphEdge } from '@/types';
import { dependencyGraphApi, dependencyApi } from '@/lib/api';

export type LayoutDirection = 'LR' | 'TB';
export type HighlightMode = 'DIRECT' | 'ALL';

export interface GraphTaskNodeData extends Record<string, unknown> {
  task: DependencyGraphNode;
  isSelected: boolean;
  isPredecessor: boolean;
  isSuccessor: boolean;
  isAncestor: boolean;
  isDescendant: boolean;
  isCritical?: boolean;
}

const NODE_WIDTH = 240;
const NODE_HEIGHT = 100;

export function useDependencyGraph(projectId?: string | null) {
  const [graph, setGraph] = useState<DependencyGraph | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [layoutDirection, setLayoutDirection] = useState<LayoutDirection>('LR');
  const [highlightMode, setHighlightMode] = useState<HighlightMode>('DIRECT');

  // Load project graph from authoritative backend endpoint
  const fetchGraph = useCallback(async () => {
    if (!projectId) {
      setGraph(null);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await dependencyGraphApi.getDependencyGraph(projectId);
      setGraph(data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unable to load the dependency graph. Try again.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchGraph();
  }, [fetchGraph]);

  // Compute direct predecessors, direct successors, ancestors, and descendants
  const {
    directPredecessors,
    directSuccessors,
    allAncestors,
    allDescendants,
  } = useMemo(() => {
    if (!graph || !selectedTaskId) {
      return {
        directPredecessors: new Set<string>(),
        directSuccessors: new Set<string>(),
        allAncestors: new Set<string>(),
        allDescendants: new Set<string>(),
      };
    }

    const directPreds = new Set<string>();
    const directSuccs = new Set<string>();
    const predMap = new Map<string, string[]>(); // node -> predecessors
    const succMap = new Map<string, string[]>(); // node -> successors

    for (const edge of graph.edges) {
      const p = edge.predecessorTaskId;
      const s = edge.successorTaskId;

      if (!predMap.has(s)) predMap.set(s, []);
      predMap.get(s)!.push(p);

      if (!succMap.has(p)) succMap.set(p, []);
      succMap.get(p)!.push(s);

      if (s === selectedTaskId) directPreds.add(p);
      if (p === selectedTaskId) directSuccs.add(s);
    }

    // Traverse all ancestors (BFS backwards)
    const ancestors = new Set<string>();
    const queueA = Array.from(directPreds);
    while (queueA.length > 0) {
      const current = queueA.shift()!;
      if (!ancestors.has(current)) {
        ancestors.add(current);
        const nextPreds = predMap.get(current) || [];
        for (const np of nextPreds) {
          if (!ancestors.has(np)) queueA.push(np);
        }
      }
    }

    // Traverse all descendants (BFS forwards)
    const descendants = new Set<string>();
    const queueD = Array.from(directSuccs);
    while (queueD.length > 0) {
      const current = queueD.shift()!;
      if (!descendants.has(current)) {
        descendants.add(current);
        const nextSuccs = succMap.get(current) || [];
        for (const ns of nextSuccs) {
          if (!descendants.has(ns)) queueD.push(ns);
        }
      }
    }

    return {
      directPredecessors: directPreds,
      directSuccessors: directSuccs,
      allAncestors: ancestors,
      allDescendants: descendants,
    };
  }, [graph, selectedTaskId]);

  // Generate Dagre layout nodes and styled edges
  const { flowNodes, flowEdges } = useMemo(() => {
    if (!graph || graph.nodes.length === 0) {
      return { flowNodes: [], flowEdges: [] };
    }

    const g = new dagre.graphlib.Graph();
    g.setDefaultEdgeLabel(() => ({}));
    g.setGraph({
      rankdir: layoutDirection,
      nodesep: 40,
      ranksep: 80,
      marginx: 30,
      marginy: 30,
    });

    // Add nodes to dagre
    for (const node of graph.nodes) {
      g.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
    }

    // Add edges to dagre
    for (const edge of graph.edges) {
      g.setEdge(edge.predecessorTaskId, edge.successorTaskId);
    }

    dagre.layout(g);

    const isHorizontal = layoutDirection === 'LR';

    // Map to ReactFlow Nodes
    const flowNodes: Node<GraphTaskNodeData>[] = graph.nodes.map((node) => {
      const pos = g.node(node.id) || { x: 0, y: 0 };
      const isSelected = node.id === selectedTaskId;
      const isPred = directPredecessors.has(node.id);
      const isSucc = directSuccessors.has(node.id);
      const isAnc = allAncestors.has(node.id);
      const isDesc = allDescendants.has(node.id);

      return {
        id: node.id,
        type: 'taskNode',
        position: {
          x: pos.x - NODE_WIDTH / 2,
          y: pos.y - NODE_HEIGHT / 2,
        },
        sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
        targetPosition: isHorizontal ? Position.Left : Position.Top,
        data: {
          task: node,
          isSelected,
          isPredecessor: isPred,
          isSuccessor: isSucc,
          isAncestor: highlightMode === 'ALL' && isAnc,
          isDescendant: highlightMode === 'ALL' && isDesc,
        },
      };
    });

    // Map to ReactFlow Edges
    const flowEdges: Edge[] = graph.edges.map((edge) => {
      const isOutboundFromSelected = edge.predecessorTaskId === selectedTaskId;
      const isInboundToSelected = edge.successorTaskId === selectedTaskId;
      const isChainEdge =
        (allAncestors.has(edge.predecessorTaskId) && allAncestors.has(edge.successorTaskId)) ||
        (allDescendants.has(edge.predecessorTaskId) && allDescendants.has(edge.successorTaskId)) ||
        (allAncestors.has(edge.predecessorTaskId) && edge.successorTaskId === selectedTaskId) ||
        (edge.predecessorTaskId === selectedTaskId && allDescendants.has(edge.successorTaskId));

      let strokeColor = '#3f3f46'; // zinc-700 default
      let strokeWidth = 1.5;
      let isAnimated = false;

      if (isOutboundFromSelected) {
        strokeColor = '#38bdf8'; // sky-400 (successor path)
        strokeWidth = 2.5;
        isAnimated = true;
      } else if (isInboundToSelected) {
        strokeColor = '#fbbf24'; // amber-400 (predecessor path)
        strokeWidth = 2.5;
        isAnimated = true;
      } else if (highlightMode === 'ALL' && isChainEdge) {
        strokeColor = '#a1a1aa'; // zinc-400
        strokeWidth = 2;
      }

      return {
        id: edge.id,
        source: edge.predecessorTaskId,
        target: edge.successorTaskId,
        type: 'smoothstep',
        animated: isAnimated,
        style: {
          stroke: strokeColor,
          strokeWidth,
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: strokeColor,
          width: 16,
          height: 16,
        },
      };
    });

    return { flowNodes, flowEdges };
  }, [
    graph,
    layoutDirection,
    selectedTaskId,
    directPredecessors,
    directSuccessors,
    allAncestors,
    allDescendants,
    highlightMode,
  ]);

  // Selected task object
  const selectedTask = useMemo(() => {
    if (!graph || !selectedTaskId) return null;
    return graph.nodes.find((n) => n.id === selectedTaskId) || null;
  }, [graph, selectedTaskId]);

  // Detailed lists for the selected task
  const selectedTaskPredecessors = useMemo(() => {
    if (!graph || !selectedTaskId) return [];
    const predIds = new Set(
      graph.edges.filter((e) => e.successorTaskId === selectedTaskId).map((e) => e.predecessorTaskId)
    );
    return graph.nodes.filter((n) => predIds.has(n.id));
  }, [graph, selectedTaskId]);

  const selectedTaskSuccessors = useMemo(() => {
    if (!graph || !selectedTaskId) return [];
    const succIds = new Set(
      graph.edges.filter((e) => e.predecessorTaskId === selectedTaskId).map((e) => e.successorTaskId)
    );
    return graph.nodes.filter((n) => succIds.has(n.id));
  }, [graph, selectedTaskId]);

  // Graph Mutations
  const addDependency = useCallback(
    async (predecessorTaskId: string, successorTaskId: string) => {
      setActionError(null);
      try {
        await dependencyApi.createDependency({ predecessorTaskId, successorTaskId });
        await fetchGraph();
      } catch (err: unknown) {
        let msg = 'Failed to create dependency.';
        if (err && typeof err === 'object' && 'code' in err) {
          const apiErr = err as { code: string; message: string };
          if (apiErr.code === 'CYCLE_DETECTED') {
            msg = 'This dependency would create a cycle, so it was not added.';
          } else {
            msg = apiErr.message || msg;
          }
        } else if (err instanceof Error) {
          if (err.message.includes('cycle') || err.message.includes('CYCLE')) {
            msg = 'This dependency would create a cycle, so it was not added.';
          } else {
            msg = err.message;
          }
        }
        setActionError(msg);
        throw new Error(msg);
      }
    },
    [fetchGraph]
  );

  const removeDependency = useCallback(
    async (predecessorTaskId: string, successorTaskId: string) => {
      setActionError(null);
      try {
        await dependencyApi.deleteDependency(predecessorTaskId, successorTaskId);
        await fetchGraph();
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to delete dependency.';
        setActionError(msg);
        throw new Error(msg);
      }
    },
    [fetchGraph]
  );

  return {
    graph,
    loading,
    error,
    actionError,
    clearActionError: () => setActionError(null),
    flowNodes,
    flowEdges,
    selectedTaskId,
    setSelectedTaskId,
    selectedTask,
    selectedTaskPredecessors,
    selectedTaskSuccessors,
    layoutDirection,
    setLayoutDirection,
    highlightMode,
    setHighlightMode,
    refreshGraph: fetchGraph,
    addDependency,
    removeDependency,
  };
}
