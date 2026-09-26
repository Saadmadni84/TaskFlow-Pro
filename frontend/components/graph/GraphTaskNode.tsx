'use client';

import React, { memo } from 'react';
import { Handle, Position, NodeProps, Node } from '@xyflow/react';
import { GraphTaskNodeData } from '@/hooks/useDependencyGraph';
import { TaskStatus, DependencyStatus } from '@/types';

export type TaskNodeType = Node<GraphTaskNodeData, 'taskNode'>;

function getWorkflowBadge(status: TaskStatus) {
  switch (status) {
    case 'DONE':
      return { label: 'DONE', bg: 'bg-emerald-950/80', text: 'text-emerald-300', border: 'border-emerald-600/40' };
    case 'IN_PROGRESS':
      return { label: 'IN PROGRESS', bg: 'bg-blue-950/80', text: 'text-blue-300', border: 'border-blue-600/40' };
    case 'REVIEW':
      return { label: 'REVIEW', bg: 'bg-purple-950/80', text: 'text-purple-300', border: 'border-purple-600/40' };
    case 'BACKLOG':
    default:
      return { label: 'BACKLOG', bg: 'bg-zinc-800/80', text: 'text-zinc-300', border: 'border-zinc-700/50' };
  }
}

function getDependencyBadge(status: DependencyStatus) {
  if (status === 'READY') {
    return { label: 'READY', bg: 'bg-emerald-500/10', text: 'text-emerald-400', border: 'border-emerald-500/30' };
  }
  return { label: 'BLOCKED', bg: 'bg-rose-500/10', text: 'text-rose-400', border: 'border-rose-500/30' };
}

export const GraphTaskNode = memo(({ data, sourcePosition = Position.Right, targetPosition = Position.Left }: NodeProps<TaskNodeType>) => {
  const { task, isSelected, isPredecessor, isSuccessor, isAncestor, isDescendant } = data;
  const wf = getWorkflowBadge(task.workflowStatus);
  const dep = getDependencyBadge(task.dependencyStatus);

  // Border & Glow styling based on topological relationship
  let borderStyle = 'border-zinc-800/80 hover:border-zinc-700';
  let ringStyle = '';
  let badgeLabel: string | null = null;
  let badgeColor = '';

  if (isSelected) {
    borderStyle = 'border-emerald-500';
    ringStyle = 'ring-2 ring-emerald-500/30 shadow-lg shadow-emerald-500/10';
  } else if (isPredecessor) {
    borderStyle = 'border-amber-400';
    ringStyle = 'ring-2 ring-amber-400/30 shadow-md shadow-amber-400/10';
    badgeLabel = 'Prerequisite';
    badgeColor = 'bg-amber-950/90 text-amber-300 border-amber-500/40';
  } else if (isSuccessor) {
    borderStyle = 'border-sky-400';
    ringStyle = 'ring-2 ring-sky-400/30 shadow-md shadow-sky-400/10';
    badgeLabel = 'Dependent';
    badgeColor = 'bg-sky-950/90 text-sky-300 border-sky-500/40';
  } else if (isAncestor) {
    borderStyle = 'border-amber-500/50 border-dashed';
    badgeLabel = 'Ancestor';
    badgeColor = 'bg-zinc-900 text-zinc-400 border-zinc-700';
  } else if (isDescendant) {
    borderStyle = 'border-sky-500/50 border-dashed';
    badgeLabel = 'Descendant';
    badgeColor = 'bg-zinc-900 text-zinc-400 border-zinc-700';
  }

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label={`Task: ${task.title}, Status: ${task.workflowStatus}, Readiness: ${task.dependencyStatus}`}
      aria-pressed={isSelected}
      className={`relative w-[240px] rounded-xl bg-zinc-900/90 backdrop-blur-sm p-3.5 border transition-all duration-200 select-none cursor-pointer ${borderStyle} ${ringStyle}`}
    >
      {/* Target handle (inbound from prerequisites) */}
      <Handle
        type="target"
        position={targetPosition}
        className="!w-2.5 !h-2.5 !bg-zinc-400 !border-2 !border-zinc-900 hover:!bg-amber-400 transition-colors"
      />

      {/* Relationship context pill */}
      {badgeLabel && (
        <span
          className={`absolute -top-2.5 right-3 text-[10px] font-mono px-2 py-0.5 rounded-full border shadow-sm ${badgeColor}`}
        >
          {badgeLabel}
        </span>
      )}

      {/* Node Header: Title */}
      <div className="flex items-start justify-between gap-1 mb-2">
        <h4 className="text-xs font-semibold text-zinc-100 line-clamp-1 leading-snug" title={task.title}>
          {task.title}
        </h4>
        {task.durationDays != null && (
          <span className="text-[10px] font-mono text-zinc-500 shrink-0 ml-1">
            {task.durationDays}d
          </span>
        )}
      </div>

      {/* Status Badges: Workflow & Dependency Readiness */}
      <div className="flex items-center gap-1.5 mb-2">
        <span
          className={`text-[9px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded border ${wf.bg} ${wf.text} ${wf.border}`}
        >
          {wf.label}
        </span>
        <span
          className={`text-[9px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded border flex items-center gap-1 ${dep.bg} ${dep.text} ${dep.border}`}
        >
          <span
            className={`w-1.5 h-1.5 rounded-full ${
              task.dependencyStatus === 'READY' ? 'bg-emerald-400 animate-pulse' : 'bg-rose-400'
            }`}
          />
          {dep.label}
        </span>
      </div>

      {/* Schedule Dates */}
      <div className="text-[10px] font-mono text-zinc-400 flex items-center justify-between pt-1 border-t border-zinc-800/60">
        <span>
          {task.scheduledStartDate || task.plannedStartDate || 'No start'}
        </span>
        <span className="text-zinc-600">→</span>
        <span>
          {task.scheduledDueDate || 'No due'}
        </span>
      </div>

      {/* Source handle (outbound to dependents) */}
      <Handle
        type="source"
        position={sourcePosition}
        className="!w-2.5 !h-2.5 !bg-zinc-400 !border-2 !border-zinc-900 hover:!bg-sky-400 transition-colors"
      />
    </div>
  );
});

GraphTaskNode.displayName = 'GraphTaskNode';
