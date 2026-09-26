'use client';

import React, { useState } from 'react';
import { Task, TaskStatus } from '@/types';
import { TaskCard } from './TaskCard';

interface KanbanColumnProps {
  status: TaskStatus;
  label: string;
  tasks: Task[];
  prerequisitesByTask: Map<string, string[]>;
  dependentsByTask: Map<string, string[]>;
  criticalTaskIds: Set<string>;
  onMoveTask: (taskId: string, targetStatus: TaskStatus) => void;
  onEditTask: (task: Task) => void;
  onOpenDependencies: (task: Task) => void;
  onOpenAiSuggestions: (task: Task) => void;
  onDeleteTask: (taskId: string) => void;
}

const COLUMN_COLORS: Record<TaskStatus, { border: string; dot: string }> = {
  BACKLOG: { border: 'border-zinc-700/60', dot: 'bg-zinc-400' },
  IN_PROGRESS: { border: 'border-amber-500/40', dot: 'bg-amber-400' },
  REVIEW: { border: 'border-blue-500/40', dot: 'bg-blue-400' },
  DONE: { border: 'border-emerald-500/40', dot: 'bg-emerald-400' },
};

export const KanbanColumn: React.FC<KanbanColumnProps> = ({
  status,
  label,
  tasks,
  prerequisitesByTask,
  dependentsByTask,
  criticalTaskIds,
  onMoveTask,
  onEditTask,
  onOpenDependencies,
  onOpenAiSuggestions,
  onDeleteTask,
}) => {
  const [isOver, setIsOver] = useState(false);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (!isOver) setIsOver(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    // Only deactivate if leaving the column element itself
    if (e.currentTarget.contains(e.relatedTarget as Node)) return;
    setIsOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsOver(false);
    try {
      const dataStr = e.dataTransfer.getData('application/json');
      if (dataStr) {
        const { taskId } = JSON.parse(dataStr);
        if (taskId) {
          onMoveTask(taskId, status);
        }
      }
    } catch {
      // Ignore drop parsing errors
    }
  };

  const colColor = COLUMN_COLORS[status];

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      aria-label={`${label} column`}
      className={`flex flex-col flex-1 min-w-[280px] max-w-full rounded-xl bg-zinc-950/60 border p-3.5 transition-colors duration-150 ${
        isOver
          ? 'border-emerald-500/60 bg-zinc-900/60 ring-1 ring-emerald-500/30'
          : 'border-zinc-800/80'
      }`}
    >
      {/* Column Header */}
      <div className="flex items-center justify-between pb-3 border-b border-zinc-800/60 mb-3 select-none">
        <div className="flex items-center gap-2">
          <span className={`h-2 w-2 rounded-full ${colColor.dot}`} />
          <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-200">
            {label}
          </h2>
        </div>
        {/* Dynamic Task Count */}
        <span className="text-xs font-mono px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-300 border border-zinc-700/50">
          {tasks.length}
        </span>
      </div>

      {/* Task Cards Container */}
      <div className="flex-1 space-y-3 overflow-y-auto min-h-[220px]">
        {tasks.length === 0 ? (
          <div className="h-40 rounded-lg border border-dashed border-zinc-800/80 flex flex-col items-center justify-center text-xs text-zinc-600 font-mono text-center p-4">
            <span>No tasks here.</span>
            <span className="text-[10px] text-zinc-700 mt-1">Drag a task here to transition</span>
          </div>
        ) : (
          tasks.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              prerequisiteCount={(prerequisitesByTask.get(task.id) || []).length}
              dependentCount={(dependentsByTask.get(task.id) || []).length}
              isCritical={criticalTaskIds.has(task.id)}
              onEdit={onEditTask}
              onOpenDependencies={onOpenDependencies}
              onOpenAiSuggestions={onOpenAiSuggestions}
              onDelete={onDeleteTask}
              onMoveToStatus={onMoveTask}
            />
          ))
        )}
      </div>
    </div>
  );
};
