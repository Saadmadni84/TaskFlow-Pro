'use client';

import React, { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

interface SimTask {
  id: string;
  name: string;
  status: 'BACKLOG' | 'IN_PROGRESS' | 'DONE';
  dependencies: string[];
}

export default function ReadinessPage() {
  const [tasks, setTasks] = useState<SimTask[]>([
    { id: 'A', name: 'Task A (Database Schema)', status: 'IN_PROGRESS', dependencies: [] },
    { id: 'B', name: 'Task B (Backend API)', status: 'BACKLOG', dependencies: ['A'] },
    { id: 'C', name: 'Task C (Integration Tests)', status: 'BACKLOG', dependencies: ['B'] },
  ]);

  const toggleTaskStatus = (id: string) => {
    setTasks((prev) =>
      prev.map((t) => {
        if (t.id === id) {
          const nextStatus = t.status === 'DONE' ? 'IN_PROGRESS' : 'DONE';
          return { ...t, status: nextStatus };
        }
        return t;
      })
    );
  };

  const isTaskReady = (task: SimTask, allTasks: SimTask[]) => {
    if (task.status === 'DONE') return true;
    if (task.dependencies.length === 0) return true;
    return task.dependencies.every((depId) => {
      const dep = allTasks.find((t) => t.id === depId);
      return dep && dep.status === 'DONE';
    });
  };

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 4 Readiness Engine
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Derived Dependency State Propagation</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Dependency Readiness Engine
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          In TaskFlow Pro, <code className="text-emerald-400">READY</code> and <code className="text-rose-400">BLOCKED</code> are 
          server-derived states calculated automatically by evaluating whether all prerequisite tasks in the DAG have reached <code className="text-zinc-200">DONE</code>.
          Downstream tasks automatically unlock or roll back in topological order.
        </p>
      </div>

      {/* Interactive Simulation Card */}
      <Card className="space-y-6">
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <div>
            <h2 className="text-sm font-medium text-zinc-200">Live Readiness Propagation Simulator</h2>
            <p className="text-xs text-zinc-400">Click &quot;Toggle Done&quot; on Task A or B to observe multi-level unlock and automatic rollback</p>
          </div>
          <span className="text-xs font-mono text-zinc-500">Linear Chain: A → B → C</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {tasks.map((task) => {
            const ready = isTaskReady(task, tasks);
            const isDone = task.status === 'DONE';

            return (
              <div
                key={task.id}
                className={`p-4 rounded-lg border transition-all ${
                  ready
                    ? 'border-emerald-500/40 bg-emerald-950/20 shadow-sm'
                    : 'border-zinc-800/80 bg-zinc-950/60'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-mono text-zinc-400">Task {task.id}</span>
                  <Badge variant={ready ? 'ready' : 'blocked'}>
                    {isDone ? 'DONE' : ready ? 'READY' : 'BLOCKED'}
                  </Badge>
                </div>

                <div className="text-sm font-medium text-zinc-200 mb-1">{task.name}</div>
                <div className="text-xs text-zinc-400 mb-4">
                  {task.dependencies.length === 0 ? (
                    <span className="text-zinc-500">Root task (no prerequisites)</span>
                  ) : (
                    <span>
                      Prerequisites: <code className="text-zinc-300">{task.dependencies.join(', ')}</code>
                    </span>
                  )}
                </div>

                <div className="pt-3 border-t border-zinc-800/60 flex items-center justify-between">
                  <span className="text-xs text-zinc-400">Workflow: <strong className="text-zinc-200">{task.status}</strong></span>
                  <Button
                    size="sm"
                    variant={isDone ? 'outline' : 'secondary'}
                    onClick={() => toggleTaskStatus(task.id)}
                  >
                    {isDone ? 'Reopen Task' : 'Mark Done'}
                  </Button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Engine Rules */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2">
          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">Authoritative Prerequisite Rule</span>
            <p className="text-[11px] text-zinc-400">
              A task is READY if and only if every direct predecessor has achieved <code className="text-zinc-300">workflowStatus == DONE</code>.
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">Automatic Rollback</span>
            <p className="text-[11px] text-zinc-400">
              Reopening an upstream task immediately rolls back all downstream tasks to BLOCKED in topological order.
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">Server-Derived Safety</span>
            <p className="text-[11px] text-zinc-400">
              Clients cannot set <code className="text-zinc-300">dependencyStatus</code>. It is evaluated exclusively on the backend.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}
