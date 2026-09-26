'use client';

import React from 'react';
import { Button } from '@/components/ui/Button';

interface GraphEmptyStateProps {
  type: 'NO_TASKS' | 'NO_DEPENDENCIES';
  onCreateTask?: () => void;
  onOpenDependencyModal?: () => void;
}

export const GraphEmptyState: React.FC<GraphEmptyStateProps> = ({
  type,
  onCreateTask,
  onOpenDependencyModal,
}) => {
  if (type === 'NO_TASKS') {
    return (
      <div className="flex flex-col items-center justify-center p-12 text-center h-[500px] border border-dashed border-zinc-800 rounded-2xl bg-zinc-950/40">
        <div className="w-12 h-12 rounded-xl bg-zinc-900 border border-zinc-800 flex items-center justify-center mb-4 text-zinc-500">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 13h6m-3-3v6m5 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        </div>
        <h3 className="text-sm font-semibold text-zinc-200 mb-1">No tasks yet</h3>
        <p className="text-xs text-zinc-400 max-w-sm mb-6 leading-relaxed">
          Create your first task to start building and visualizing the directed acyclic graph workflow.
        </p>
        {onCreateTask && (
          <Button onClick={onCreateTask} size="sm" variant="primary">
            Create First Task
          </Button>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center p-12 text-center h-[500px] border border-dashed border-zinc-800 rounded-2xl bg-zinc-950/40">
      <div className="w-12 h-12 rounded-xl bg-zinc-900 border border-zinc-800 flex items-center justify-center mb-4 text-zinc-500">
        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
        </svg>
      </div>
      <h3 className="text-sm font-semibold text-zinc-200 mb-1">No dependencies yet</h3>
      <p className="text-xs text-zinc-400 max-w-sm mb-6 leading-relaxed">
        Connect tasks with directed dependency relationships (predecessor → successor) to construct the deterministic execution schedule.
      </p>
      {onOpenDependencyModal && (
        <Button onClick={onOpenDependencyModal} size="sm" variant="secondary">
          Add First Dependency
        </Button>
      )}
    </div>
  );
};
