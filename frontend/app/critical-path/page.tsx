import React from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';

export default function CriticalPathPage() {
  return (
    <div className="max-w-6xl mx-auto space-y-8">
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Future Module
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Critical Path Method (CPM)</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Critical Path Analysis
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          Critical Path Method (CPM) identifies the longest path of dependent tasks through the project graph,
          calculating early start/finish, late start/finish, and total float (slack).
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="space-y-2">
          <Badge variant="neutral">Algorithm</Badge>
          <div className="text-sm font-semibold text-zinc-200">Forward & Backward Pass</div>
          <p className="text-xs text-zinc-400">
            Calculates earliest possible dates and latest allowable dates across all tasks in the DAG.
          </p>
        </Card>

        <Card className="space-y-2">
          <Badge variant="neutral">Metric</Badge>
          <div className="text-sm font-semibold text-zinc-200">Zero Total Float (Slack)</div>
          <p className="text-xs text-zinc-400">
            Tasks with zero slack cannot be delayed without directly pushing back the project delivery date.
          </p>
        </Card>

        <Card className="space-y-2">
          <Badge variant="neutral">Visualization</Badge>
          <div className="text-sm font-semibold text-zinc-200">Critical Chain Highlighting</div>
          <p className="text-xs text-zinc-400">
            Visual red highlight on the Kanban and timeline views for critical path tasks.
          </p>
        </Card>
      </div>
    </div>
  );
}
