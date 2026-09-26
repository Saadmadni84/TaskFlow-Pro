import React from 'react';
import { Badge } from '@/components/ui/Badge';

export const Header: React.FC = () => {
  return (
    <header className="h-14 border-b border-zinc-800/80 bg-zinc-950/70 backdrop-blur-md px-6 flex items-center justify-between sticky top-0 z-20">
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          <div className="h-5 w-5 rounded bg-zinc-100 flex items-center justify-center">
            <div className="h-2.5 w-2.5 rounded-sm bg-zinc-950 rotate-45" />
          </div>
          <span className="font-semibold text-sm tracking-tight text-zinc-100">
            TaskFlow <span className="text-zinc-500 font-normal">Pro</span>
          </span>
        </div>
        <span className="text-xs text-zinc-600">/</span>
        <span className="text-xs font-medium text-zinc-400">DAG Scheduling Engine</span>
      </div>

      <div className="flex items-center gap-3">
        <Badge variant="neutral">Phase 1 Foundation</Badge>
        <div className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" title="System Operational" />
      </div>
    </header>
  );
};
