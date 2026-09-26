'use client';

import React from 'react';
import { DependencyGraphNode } from '@/types';
import { Button } from '@/components/ui/Button';

interface GraphDetailDrawerProps {
  task: DependencyGraphNode;
  predecessors: DependencyGraphNode[];
  successors: DependencyGraphNode[];
  onClose: () => void;
  onSelectTask: (taskId: string) => void;
  onRemoveDependency: (predId: string, succId: string) => Promise<void>;
  onOpenAddDependency: () => void;
  onNavigateToKanban?: (taskId: string) => void;
}

export const GraphDetailDrawer: React.FC<GraphDetailDrawerProps> = ({
  task,
  predecessors,
  successors,
  onClose,
  onSelectTask,
  onRemoveDependency,
  onOpenAddDependency,
  onNavigateToKanban,
}) => {
  return (
    <aside
      aria-label={`Details for task ${task.title}`}
      className="w-80 shrink-0 border-l border-zinc-800/80 bg-zinc-950/80 backdrop-blur-md p-5 flex flex-col justify-between overflow-y-auto z-10"
    >
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-start justify-between gap-2 border-b border-zinc-800/80 pb-3">
          <div className="space-y-1">
            <span className="text-[10px] font-mono text-zinc-500 uppercase tracking-widest">
              Task Details
            </span>
            <h3 className="text-sm font-semibold text-zinc-100 leading-snug">
              {task.title}
            </h3>
          </div>
          <button
            onClick={onClose}
            aria-label="Close task details"
            className="text-zinc-500 hover:text-zinc-300 p-1 rounded-md hover:bg-zinc-800/50 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Status & Readiness */}
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="p-2.5 rounded-lg bg-zinc-900/60 border border-zinc-800/80 space-y-1">
            <span className="text-[10px] font-mono text-zinc-500 uppercase">Workflow</span>
            <div className="font-semibold text-zinc-200">
              {task.workflowStatus.replace('_', ' ')}
            </div>
          </div>
          <div className="p-2.5 rounded-lg bg-zinc-900/60 border border-zinc-800/80 space-y-1">
            <span className="text-[10px] font-mono text-zinc-500 uppercase">Readiness</span>
            <div className={`font-semibold flex items-center gap-1.5 ${
              task.dependencyStatus === 'READY' ? 'text-emerald-400' : 'text-rose-400'
            }`}>
              <span className={`w-1.5 h-1.5 rounded-full ${
                task.dependencyStatus === 'READY' ? 'bg-emerald-400' : 'bg-rose-400'
              }`} />
              {task.dependencyStatus}
            </div>
          </div>
        </div>

        {/* Schedule dates */}
        <div className="p-3 rounded-lg bg-zinc-900/40 border border-zinc-800/80 space-y-2 text-xs">
          <div className="flex justify-between text-zinc-400">
            <span>Scheduled Start:</span>
            <span className="font-mono text-zinc-200">{task.scheduledStartDate || '—'}</span>
          </div>
          <div className="flex justify-between text-zinc-400">
            <span>Scheduled Due:</span>
            <span className="font-mono text-zinc-200">{task.scheduledDueDate || '—'}</span>
          </div>
          {task.durationDays != null && (
            <div className="flex justify-between text-zinc-400 pt-1 border-t border-zinc-800/60">
              <span>Preserved Duration:</span>
              <span className="font-mono text-zinc-200">{task.durationDays} days</span>
            </div>
          )}
        </div>

        {/* Direct Prerequisites (Inbound) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs">
            <span className="font-semibold text-amber-400 flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-amber-400" />
              Prerequisites ({predecessors.length})
            </span>
            <span className="text-[10px] text-zinc-500 font-mono">Must finish first</span>
          </div>

          {predecessors.length === 0 ? (
            <p className="text-[11px] text-zinc-500 italic p-2 rounded bg-zinc-900/30 border border-zinc-800/40">
              No prerequisites. Task is an initial root.
            </p>
          ) : (
            <div className="space-y-1.5 max-h-36 overflow-y-auto">
              {predecessors.map((p) => (
                <div
                  key={p.id}
                  className="flex items-center justify-between p-2 rounded-lg bg-zinc-900/70 border border-amber-500/20 text-xs hover:border-amber-500/40 transition-colors"
                >
                  <button
                    onClick={() => onSelectTask(p.id)}
                    className="text-left font-medium text-zinc-200 hover:text-amber-300 line-clamp-1 flex-1 pr-2"
                  >
                    {p.title}
                  </button>
                  <button
                    onClick={() => onRemoveDependency(p.id, task.id)}
                    title="Remove dependency"
                    aria-label={`Remove prerequisite ${p.title}`}
                    className="text-zinc-500 hover:text-rose-400 p-1 rounded"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Direct Dependents (Outbound) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs">
            <span className="font-semibold text-sky-400 flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-sky-400" />
              Dependents ({successors.length})
            </span>
            <span className="text-[10px] text-zinc-500 font-mono">Blocked by this</span>
          </div>

          {successors.length === 0 ? (
            <p className="text-[11px] text-zinc-500 italic p-2 rounded bg-zinc-900/30 border border-zinc-800/40">
              No dependents. Task is a terminal sink.
            </p>
          ) : (
            <div className="space-y-1.5 max-h-36 overflow-y-auto">
              {successors.map((s) => (
                <div
                  key={s.id}
                  className="flex items-center justify-between p-2 rounded-lg bg-zinc-900/70 border border-sky-500/20 text-xs hover:border-sky-500/40 transition-colors"
                >
                  <button
                    onClick={() => onSelectTask(s.id)}
                    className="text-left font-medium text-zinc-200 hover:text-sky-300 line-clamp-1 flex-1 pr-2"
                  >
                    {s.title}
                  </button>
                  <button
                    onClick={() => onRemoveDependency(task.id, s.id)}
                    title="Remove dependency"
                    aria-label={`Remove dependent ${s.title}`}
                    className="text-zinc-500 hover:text-rose-400 p-1 rounded"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Action Footer */}
      <div className="pt-4 border-t border-zinc-800/80 space-y-2">
        <Button
          onClick={onOpenAddDependency}
          size="sm"
          variant="secondary"
          className="w-full justify-center text-xs"
        >
          <svg className="w-3.5 h-3.5 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Manage Dependencies
        </Button>
        {onNavigateToKanban && (
          <Button
            onClick={() => onNavigateToKanban(task.id)}
            size="sm"
            variant="outline"
            className="w-full justify-center text-xs"
          >
            View in Kanban
          </Button>
        )}
      </div>
    </aside>
  );
};
