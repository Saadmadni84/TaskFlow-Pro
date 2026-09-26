import React from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';

export default function KanbanPage() {
  const columns = [
    { name: 'Backlog', count: 4, variant: 'neutral' as const },
    { name: 'In Progress', count: 2, variant: 'warning' as const },
    { name: 'Review', count: 1, variant: 'ready' as const },
    { name: 'Done', count: 6, variant: 'success' as const },
  ];

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-amber-400 uppercase tracking-widest">
            Phase 7 Upcoming
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Interactive Drag-and-Drop Board</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Dependency-Aware Kanban Board
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          The upcoming Phase 7 introduces an interactive four-column Kanban board powered by <code className="text-zinc-200">@dnd-kit</code>.
          Tasks in <code className="text-rose-400">BLOCKED</code> status are visually constrained from premature execution,
          and moving a task to <code className="text-emerald-400">DONE</code> automatically unlocks downstream successors in real time.
        </p>
      </div>

      {/* Kanban Preview Columns */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {columns.map((col) => (
          <div key={col.name} className="p-4 rounded-lg bg-zinc-950/60 border border-zinc-800/80 space-y-3">
            <div className="flex items-center justify-between border-b border-zinc-800/60 pb-2">
              <span className="text-xs font-semibold text-zinc-200">{col.name}</span>
              <Badge variant={col.variant}>{col.count}</Badge>
            </div>
            <div className="h-40 rounded border border-dashed border-zinc-800 flex items-center justify-center text-xs text-zinc-600 font-mono">
              Ready for Phase 7
            </div>
          </div>
        ))}
      </div>

      <Card className="space-y-2">
        <div className="text-xs font-mono uppercase text-zinc-400">Planned Integration Highlights</div>
        <ul className="text-xs text-zinc-400 space-y-1 list-disc list-inside leading-relaxed">
          <li>Visual dependency badges indicating prerequisite status on cards.</li>
          <li>Drag-and-drop movement validation preventing blocked tasks from entering In Progress.</li>
          <li>Real-time readiness propagation upon task completion.</li>
        </ul>
      </Card>
    </div>
  );
}
