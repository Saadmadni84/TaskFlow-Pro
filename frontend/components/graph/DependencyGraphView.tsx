'use client';

import React, { useState, useMemo } from 'react';
import {
  ReactFlow,
  Controls,
  Background,
  BackgroundVariant,
  NodeMouseHandler,
  NodeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { useDependencyGraph } from '@/hooks/useDependencyGraph';
import { GraphTaskNode } from './GraphTaskNode';
import { GraphDetailDrawer } from './GraphDetailDrawer';
import { GraphEmptyState } from './GraphEmptyState';
import { DependencyModal } from '@/components/dependency/DependencyModal';
import { Button } from '@/components/ui/Button';
import { Task } from '@/types';

interface DependencyGraphViewProps {
  projectId?: string | null;
  onNavigateToKanban?: (taskId: string) => void;
  onCreateTask?: () => void;
}

const nodeTypes: NodeTypes = {
  taskNode: GraphTaskNode,
};

export const DependencyGraphView: React.FC<DependencyGraphViewProps> = ({
  projectId,
  onNavigateToKanban,
  onCreateTask,
}) => {
  const {
    graph,
    loading,
    error,
    actionError,
    clearActionError,
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
    refreshGraph,
    addDependency,
    removeDependency,
  } = useDependencyGraph(projectId);

  // Accessible Text Alternative View toggle
  const [isListView, setIsListView] = useState(false);

  // Dependency Management Modal
  const [isDependencyModalOpen, setIsDependencyModalOpen] = useState(false);

  // Node selection callback
  const onNodeClick: NodeMouseHandler = (_, node) => {
    setSelectedTaskId(node.id === selectedTaskId ? null : node.id);
  };

  const onPaneClick = () => {
    setSelectedTaskId(null);
  };

  // Convert graph node to full Task format for DependencyModal reuse
  const selectedTaskAsTask: Task | null = useMemo(() => {
    if (!selectedTask || !projectId) return null;
    return {
      id: selectedTask.id,
      projectId,
      title: selectedTask.title,
      workflowStatus: selectedTask.workflowStatus,
      dependencyStatus: selectedTask.dependencyStatus,
      scheduledStartDate: selectedTask.scheduledStartDate,
      scheduledDueDate: selectedTask.scheduledDueDate,
      plannedStartDate: selectedTask.plannedStartDate,
      durationDays: selectedTask.durationDays ?? 1,
      version: 0,
      createdAt: '',
      updatedAt: '',
    };
  }, [selectedTask, projectId]);

  // All tasks in project formatted for DependencyModal
  const allProjectTasks: Task[] = useMemo(() => {
    if (!graph || !projectId) return [];
    return graph.nodes.map((n) => ({
      id: n.id,
      projectId,
      title: n.title,
      workflowStatus: n.workflowStatus,
      dependencyStatus: n.dependencyStatus,
      scheduledStartDate: n.scheduledStartDate,
      scheduledDueDate: n.scheduledDueDate,
      plannedStartDate: n.plannedStartDate,
      durationDays: n.durationDays ?? 1,
      version: 0,
      createdAt: '',
      updatedAt: '',
    }));
  }, [graph, projectId]);

  // All dependencies in project formatted for DependencyModal
  const allProjectDependencies = useMemo(() => {
    if (!graph) return [];
    return graph.edges.map((e) => ({
      id: e.id,
      predecessorTaskId: e.predecessorTaskId,
      successorTaskId: e.successorTaskId,
      createdAt: '',
    }));
  }, [graph]);

  if (loading && !graph) {
    return (
      <div className="flex flex-col items-center justify-center h-[600px] border border-zinc-800 rounded-2xl bg-zinc-950/40">
        <div className="flex items-center gap-3 text-zinc-400">
          <div className="w-5 h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-xs font-mono">Loading project dependency graph...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-[500px] p-8 border border-zinc-800 rounded-2xl bg-zinc-950/40 text-center">
        <div className="w-10 h-10 rounded-full bg-rose-950/50 border border-rose-500/30 flex items-center justify-center text-rose-400 mb-3">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>
        <h3 className="text-sm font-semibold text-zinc-200 mb-1">Unable to load the dependency graph</h3>
        <p className="text-xs text-zinc-400 max-w-sm mb-4 leading-relaxed">{error}</p>
        <Button onClick={refreshGraph} size="sm" variant="secondary">
          Try Again
        </Button>
      </div>
    );
  }

  if (!graph || graph.nodes.length === 0) {
    return (
      <GraphEmptyState
        type="NO_TASKS"
        onCreateTask={onCreateTask}
        onOpenDependencyModal={() => setIsDependencyModalOpen(true)}
      />
    );
  }

  return (
    <div className="space-y-4">
      {/* Action / Cycle Detection Error Alert */}
      {actionError && (
        <div
          role="alert"
          className="flex items-center justify-between p-3.5 rounded-xl bg-rose-950/40 border border-rose-500/40 text-xs text-rose-300"
        >
          <div className="flex items-center gap-2">
            <svg className="w-4 h-4 shrink-0 text-rose-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{actionError}</span>
          </div>
          <button
            onClick={clearActionError}
            aria-label="Dismiss error"
            className="text-rose-400 hover:text-rose-200 p-1"
          >
            ✕
          </button>
        </div>
      )}

      {/* Graph Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-xl bg-zinc-900/60 border border-zinc-800/80">
        {/* Left Controls: Legend & Metrics */}
        <div className="flex items-center gap-4 text-xs font-mono">
          <div className="flex items-center gap-1.5 text-zinc-300">
            <span className="w-2 h-2 rounded-full bg-emerald-400" />
            <span>{graph.nodes.length} Tasks</span>
          </div>
          <div className="flex items-center gap-1.5 text-zinc-400">
            <span className="w-2 h-2 rounded-full bg-zinc-500" />
            <span>{graph.edges.length} Dependencies</span>
          </div>
          <div className="hidden sm:flex items-center gap-3 pl-3 border-l border-zinc-800 text-[11px] text-zinc-400">
            <span className="flex items-center gap-1">
              <span className="w-2 h-0.5 bg-amber-400 inline-block" /> Prerequisites
            </span>
            <span className="flex items-center gap-1">
              <span className="w-2 h-0.5 bg-sky-400 inline-block" /> Dependents
            </span>
          </div>
        </div>

        {/* Right Controls: View Settings */}
        <div className="flex items-center gap-2">
          {/* Direction toggle */}
          <div className="flex rounded-lg bg-zinc-950 p-0.5 border border-zinc-800 text-[11px] font-mono">
            <button
              onClick={() => setLayoutDirection('LR')}
              aria-label="Left to Right layout"
              className={`px-2 py-1 rounded transition-all ${
                layoutDirection === 'LR' ? 'bg-zinc-800 text-zinc-100 font-semibold' : 'text-zinc-500 hover:text-zinc-300'
              }`}
            >
              Left → Right
            </button>
            <button
              onClick={() => setLayoutDirection('TB')}
              aria-label="Top to Bottom layout"
              className={`px-2 py-1 rounded transition-all ${
                layoutDirection === 'TB' ? 'bg-zinc-800 text-zinc-100 font-semibold' : 'text-zinc-500 hover:text-zinc-300'
              }`}
            >
              Top → Down
            </button>
          </div>

          {/* Highlight mode */}
          <button
            onClick={() => setHighlightMode(highlightMode === 'DIRECT' ? 'ALL' : 'DIRECT')}
            title="Toggle direct vs full dependency chain highlighting"
            className={`px-2.5 py-1.5 rounded-lg border text-xs font-mono transition-all ${
              highlightMode === 'ALL'
                ? 'bg-zinc-800 text-emerald-400 border-emerald-500/40 font-medium'
                : 'bg-zinc-950 text-zinc-400 border-zinc-800 hover:text-zinc-200'
            }`}
          >
            Chain: {highlightMode === 'DIRECT' ? 'Direct' : 'Full (Ancestors)'}
          </button>

          {/* List vs Visual toggle */}
          <button
            onClick={() => setIsListView(!isListView)}
            title="Toggle between graphical canvas and accessible list view"
            className="px-2.5 py-1.5 rounded-lg bg-zinc-950 text-zinc-400 border border-zinc-800 hover:text-zinc-200 text-xs font-mono"
          >
            {isListView ? 'Visual Canvas' : 'Accessible List'}
          </button>

          {/* Refresh */}
          <Button onClick={refreshGraph} size="sm" variant="ghost" title="Refresh graph">
            <svg className="w-3.5 h-3.5 text-zinc-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </Button>
        </div>
      </div>

      {/* Main View Area: Visual Canvas vs Accessible List */}
      {isListView ? (
        /* Accessible Text Alternative View */
        <div
          role="region"
          aria-label="Accessible Dependency List View"
          className="p-6 rounded-2xl bg-zinc-900/50 border border-zinc-800/80 space-y-4 max-h-[650px] overflow-y-auto"
        >
          <div className="border-b border-zinc-800 pb-3">
            <h3 className="text-sm font-semibold text-zinc-100">Topological Task Relationships (Accessible)</h3>
            <p className="text-xs text-zinc-400">Structured screen-reader friendly overview of all project dependencies.</p>
          </div>

          <div className="divide-y divide-zinc-800/60">
            {graph.nodes.map((node) => {
              const preds = graph.edges
                .filter((e) => e.successorTaskId === node.id)
                .map((e) => graph.nodes.find((n) => n.id === e.predecessorTaskId)?.title)
                .filter(Boolean);

              const succs = graph.edges
                .filter((e) => e.predecessorTaskId === node.id)
                .map((e) => graph.nodes.find((n) => n.id === e.successorTaskId)?.title)
                .filter(Boolean);

              return (
                <div key={node.id} className="py-3.5 space-y-2 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-zinc-200 text-sm">{node.title}</span>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-[10px] text-zinc-400 px-2 py-0.5 rounded bg-zinc-800">
                        {node.workflowStatus}
                      </span>
                      <span className={`font-mono text-[10px] px-2 py-0.5 rounded ${
                        node.dependencyStatus === 'READY' ? 'text-emerald-400 bg-emerald-950/60' : 'text-rose-400 bg-rose-950/60'
                      }`}>
                        {node.dependencyStatus}
                      </span>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-zinc-400 pl-2">
                    <div>
                      <span className="text-amber-400 font-medium">Prerequisites ({preds.length}): </span>
                      {preds.length > 0 ? preds.join(', ') : 'None (Root Task)'}
                    </div>
                    <div>
                      <span className="text-sky-400 font-medium">Dependents ({succs.length}): </span>
                      {succs.length > 0 ? succs.join(', ') : 'None (Terminal Sink)'}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      ) : (
        /* ReactFlow Visual Canvas */
        <div className="relative flex h-[650px] w-full rounded-2xl border border-zinc-800/80 bg-zinc-950/90 overflow-hidden shadow-inner">
          <div className="flex-1 h-full w-full">
            <ReactFlow
              nodes={flowNodes}
              edges={flowEdges}
              nodeTypes={nodeTypes}
              onNodeClick={onNodeClick}
              onPaneClick={onPaneClick}
              fitView
              minZoom={0.2}
              maxZoom={2.0}
              proOptions={{ hideAttribution: true }}
            >
              <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#27272a" />
              <Controls className="!bg-zinc-900 !border-zinc-800 !rounded-xl !shadow-lg [&>button]:!border-zinc-800 [&>button]:!text-zinc-300 [&>button:hover]:!bg-zinc-800" />
            </ReactFlow>
          </div>

          {/* Selected Task Details Drawer */}
          {selectedTask && (
            <GraphDetailDrawer
              task={selectedTask}
              predecessors={selectedTaskPredecessors}
              successors={selectedTaskSuccessors}
              onClose={() => setSelectedTaskId(null)}
              onSelectTask={(id) => setSelectedTaskId(id)}
              onRemoveDependency={async (predId, succId) => {
                await removeDependency(predId, succId);
              }}
              onOpenAddDependency={() => setIsDependencyModalOpen(true)}
              onNavigateToKanban={onNavigateToKanban}
            />
          )}
        </div>
      )}

      {/* Reuse Authoritative DependencyModal for Adding/Managing Dependencies */}
      {isDependencyModalOpen && selectedTaskAsTask && (
        <DependencyModal
          task={selectedTaskAsTask}
          allTasks={allProjectTasks}
          dependencies={allProjectDependencies}
          isOpen={isDependencyModalOpen}
          onClose={() => setIsDependencyModalOpen(false)}
          onAddDependency={async (predId, succId) => {
            try {
              await addDependency(predId, succId);
              return { success: true };
            } catch (err: unknown) {
              const errorMsg = err instanceof Error ? err.message : 'Failed to add dependency';
              return { success: false, error: errorMsg };
            }
          }}
          onRemoveDependency={async (predId, succId) => {
            try {
              await removeDependency(predId, succId);
              return true;
            } catch {
              return false;
            }
          }}
        />
      )}
    </div>
  );
};
