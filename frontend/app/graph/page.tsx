'use client';

import React, { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { KanbanWorkspace } from '@/components/board/KanbanWorkspace';

function GraphPageContent() {
  const searchParams = useSearchParams();
  const critical = searchParams.get('critical') === 'true';
  const taskId = searchParams.get('taskId');

  return (
    <KanbanWorkspace
      initialView="GRAPH"
      initialCriticalMode={critical}
      initialSelectedTaskId={taskId}
    />
  );
}

export default function GraphPage() {
  return (
    <Suspense
      fallback={
        <div className="flex items-center justify-center h-80 text-xs font-mono text-zinc-400">
          Loading DAG Graph...
        </div>
      }
    >
      <GraphPageContent />
    </Suspense>
  );
}
