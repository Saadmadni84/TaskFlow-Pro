'use client';

import React, { useState } from 'react';
import { Task, TaskDependency } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';

interface DependencyModalProps {
  isOpen: boolean;
  task: Task | null;
  allTasks: Task[];
  dependencies: TaskDependency[];
  onClose: () => void;
  onAddDependency: (predecessorId: string, successorId: string) => Promise<{ success: boolean; error?: string }>;
  onRemoveDependency: (predecessorId: string, successorId: string) => Promise<boolean>;
}

export const DependencyModal: React.FC<DependencyModalProps> = ({
  isOpen,
  task,
  allTasks,
  dependencies,
  onClose,
  onAddDependency,
  onRemoveDependency,
}) => {
  const [selectedPredecessorId, setSelectedPredecessorId] = useState('');
  const [adding, setAdding] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (!task) return null;

  // Find prerequisites: dependencies where successorTaskId === task.id
  const prerequisiteEdges = dependencies.filter((d) => d.successorTaskId === task.id);
  const prerequisiteTaskIds = new Set(prerequisiteEdges.map((d) => d.predecessorTaskId));
  const prerequisiteTasks = allTasks.filter((t) => prerequisiteTaskIds.has(t.id));

  // Find dependents: dependencies where predecessorTaskId === task.id
  const dependentEdges = dependencies.filter((d) => d.predecessorTaskId === task.id);
  const dependentTaskIds = new Set(dependentEdges.map((d) => d.successorTaskId));
  const dependentTasks = allTasks.filter((t) => dependentTaskIds.has(t.id));

  // Eligible tasks to add as prerequisites: all tasks except this task and existing prerequisites
  const eligibleTasks = allTasks.filter(
    (t) => t.id !== task.id && !prerequisiteTaskIds.has(t.id)
  );

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPredecessorId) return;

    setErrorMsg(null);
    setAdding(true);
    try {
      const result = await onAddDependency(selectedPredecessorId, task.id);
      if (result.success) {
        setSelectedPredecessorId('');
      } else if (result.error) {
        setErrorMsg(result.error);
      }
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (predecessorId: string) => {
    setErrorMsg(null);
    setRemovingId(predecessorId);
    try {
      await onRemoveDependency(predecessorId, task.id);
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Task Dependencies"
      description={`Manage predecessor and successor relationships for "${task.title}".`}
      maxWidth="lg"
    >
      <div className="space-y-5">
        {errorMsg && (
          <div className="p-3 rounded-lg bg-rose-950/50 border border-rose-800/50 text-rose-300 text-xs flex items-start gap-2">
            <svg className="w-4 h-4 shrink-0 text-rose-400 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <div>
              <div className="font-semibold">Dependency Error</div>
              <div className="mt-0.5 leading-relaxed">{errorMsg}</div>
            </div>
          </div>
        )}

        {/* Section 1: Prerequisites (Must finish before this task starts) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-mono uppercase tracking-wider text-zinc-300">
              Prerequisites ({prerequisiteTasks.length})
            </h3>
            <span className="text-[10px] text-zinc-500 font-mono">Must complete first</span>
          </div>

          {prerequisiteTasks.length === 0 ? (
            <div className="p-4 rounded-lg bg-zinc-950 border border-zinc-800/80 text-xs text-zinc-500 text-center font-mono">
              No prerequisites. This task can start independently.
            </div>
          ) : (
            <div className="space-y-1.5 max-h-44 overflow-y-auto">
              {prerequisiteTasks.map((prereq) => (
                <div
                  key={prereq.id}
                  className="p-2.5 rounded-lg bg-zinc-950/70 border border-zinc-800 flex items-center justify-between text-xs"
                >
                  <div className="flex items-center gap-2 truncate">
                    <span
                      className={`h-2 w-2 rounded-full shrink-0 ${
                        prereq.workflowStatus === 'DONE' ? 'bg-emerald-400' : 'bg-amber-400'
                      }`}
                    />
                    <span className="font-medium text-zinc-200 truncate">{prereq.title}</span>
                    <span className="text-[10px] font-mono text-zinc-500 px-1.5 py-0.5 rounded bg-zinc-900 border border-zinc-800">
                      {prereq.workflowStatus}
                    </span>
                  </div>

                  <Button
                    variant="danger"
                    size="sm"
                    disabled={removingId === prereq.id}
                    onClick={() => handleRemove(prereq.id)}
                    className="text-[11px] h-7 px-2"
                  >
                    {removingId === prereq.id ? 'Removing...' : 'Remove'}
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Add Prerequisite Form */}
        <form onSubmit={handleAdd} className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 space-y-2">
          <label htmlFor="prereq-select" className="block text-xs font-medium text-zinc-300">
            Add Prerequisite Task
          </label>
          <div className="flex gap-2">
            <select
              id="prereq-select"
              value={selectedPredecessorId}
              onChange={(e) => setSelectedPredecessorId(e.target.value)}
              disabled={adding || eligibleTasks.length === 0}
              className="flex-1 px-3 py-1.5 text-xs rounded-md bg-zinc-900 border border-zinc-700/80 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-400"
            >
              <option value="">
                {eligibleTasks.length === 0 ? 'No tasks available to add' : 'Select a predecessor task...'}
              </option>
              {eligibleTasks.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.title} ({t.workflowStatus})
                </option>
              ))}
            </select>
            <Button
              variant="primary"
              size="sm"
              type="submit"
              disabled={!selectedPredecessorId || adding}
              className="text-xs shrink-0"
            >
              {adding ? 'Validating...' : 'Add Dependency'}
            </Button>
          </div>
          <p className="text-[10px] text-zinc-500 font-mono">
            Server performs automatic cycle detection and recalculates schedule & readiness.
          </p>
        </form>

        {/* Section 2: Dependents (Tasks waiting on this task) */}
        <div className="space-y-2 pt-2 border-t border-zinc-800/80">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-mono uppercase tracking-wider text-zinc-300">
              Dependents ({dependentTasks.length})
            </h3>
            <span className="text-[10px] text-zinc-500 font-mono">Waiting on this task</span>
          </div>

          {dependentTasks.length === 0 ? (
            <div className="p-4 rounded-lg bg-zinc-950 border border-zinc-800/80 text-xs text-zinc-500 text-center font-mono">
              No tasks currently depend on this task.
            </div>
          ) : (
            <div className="space-y-1.5 max-h-44 overflow-y-auto">
              {dependentTasks.map((dep) => (
                <div
                  key={dep.id}
                  className="p-2.5 rounded-lg bg-zinc-950/70 border border-zinc-800 flex items-center justify-between text-xs"
                >
                  <div className="flex items-center gap-2 truncate">
                    <span
                      className={`h-2 w-2 rounded-full shrink-0 ${
                        dep.dependencyStatus === 'BLOCKED' ? 'bg-rose-400' : 'bg-emerald-400'
                      }`}
                    />
                    <span className="font-medium text-zinc-200 truncate">{dep.title}</span>
                    <span className="text-[10px] font-mono text-zinc-500 px-1.5 py-0.5 rounded bg-zinc-900 border border-zinc-800">
                      {dep.dependencyStatus}
                    </span>
                  </div>
                  <span className="text-[11px] font-mono text-zinc-500">{dep.workflowStatus}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="pt-3 border-t border-zinc-800/80 flex justify-end">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Done
          </Button>
        </div>
      </div>
    </Modal>
  );
};
