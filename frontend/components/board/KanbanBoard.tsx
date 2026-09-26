'use client';

import React from 'react';
import { Task, TaskStatus } from '@/types';
import { KanbanColumn } from './KanbanColumn';
import { Button } from '@/components/ui/Button';

interface KanbanBoardProps {
  tasks: Task[];
  prerequisitesByTask: Map<string, string[]>;
  dependentsByTask: Map<string, string[]>;
  criticalTaskIds: Set<string>;
  onMoveTask: (taskId: string, targetStatus: TaskStatus) => void;
  onEditTask: (task: Task) => void;
  onOpenDependencies: (task: Task) => void;
  onOpenAiSuggestions: (task: Task) => void;
  onDeleteTask: (taskId: string) => void;
  onCreateTaskClick: () => void;
}

const COLUMNS: { status: TaskStatus; label: string }[] = [
  { status: 'BACKLOG', label: 'Backlog' },
  { status: 'IN_PROGRESS', label: 'In Progress' },
  { status: 'REVIEW', label: 'Review' },
  { status: 'DONE', label: 'Done' },
];

export const KanbanBoard: React.FC<KanbanBoardProps> = ({
  tasks,
  prerequisitesByTask,
  dependentsByTask,
  criticalTaskIds,
  onMoveTask,
  onEditTask,
  onOpenDependencies,
  onOpenAiSuggestions,
  onDeleteTask,
  onCreateTaskClick,
}) => {
  // Check if project has no tasks at all
  if (tasks.length === 0) {
    return (
      <div className="h-96 rounded-xl border border-dashed border-zinc-800 bg-zinc-950/40 flex flex-col items-center justify-center p-8 text-center space-y-4">
        <div className="h-12 w-12 rounded-full bg-zinc-900 border border-zinc-800 flex items-center justify-center text-zinc-500">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
        </div>
        <div>
          <h3 className="text-base font-semibold text-zinc-200">No tasks yet</h3>
          <p className="text-xs text-zinc-400 mt-1 max-w-sm">
            Create your first task to start building the workflow. Dependencies and schedule constraints will compute automatically.
          </p>
        </div>
        <Button variant="primary" size="sm" onClick={onCreateTaskClick}>
          + Create Task
        </Button>
      </div>
    );
  }

  return (
    <div className="w-full overflow-x-auto pb-4">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 min-w-[300px]">
        {COLUMNS.map((col) => {
          const colTasks = tasks.filter((t) => t.workflowStatus === col.status);
          return (
            <KanbanColumn
              key={col.status}
              status={col.status}
              label={col.label}
              tasks={colTasks}
              prerequisitesByTask={prerequisitesByTask}
              dependentsByTask={dependentsByTask}
              criticalTaskIds={criticalTaskIds}
              onMoveTask={onMoveTask}
              onEditTask={onEditTask}
              onOpenDependencies={onOpenDependencies}
              onOpenAiSuggestions={onOpenAiSuggestions}
              onDeleteTask={onDeleteTask}
            />
          );
        })}
      </div>
    </div>
  );
};
