'use client';

import React from 'react';
import { usePathname } from 'next/navigation';
import { Badge } from '@/components/ui/Badge';

const ROUTE_INFO: Record<string, { title: string; phase: string }> = {
  '/': { title: 'Architecture & Foundation', phase: 'Phase 1' },
  '/domain-model': { title: 'Domain Persistence & PostgreSQL', phase: 'Phase 2' },
  '/dag-engine': { title: 'Deterministic DAG Engine', phase: 'Phase 3' },
  '/readiness': { title: 'Dependency Readiness Engine', phase: 'Phase 4' },
  '/scheduling': { title: 'Dependency-Aware Scheduling Engine', phase: 'Phase 5' },
  '/impact-preview': { title: 'Dependency Impact Preview', phase: 'Phase 6' },
  '/kanban': { title: 'Interactive Kanban Board', phase: 'Phase 7' },
  '/critical-path': { title: 'Critical Path Analysis', phase: 'Planned' },
  '/ai-suggestions': { title: 'AI Dependency Suggestions', phase: 'Planned' },
};

export const Header: React.FC = () => {
  const pathname = usePathname();
  const current = ROUTE_INFO[pathname] || { title: 'DAG Scheduling Platform', phase: 'TaskFlow Pro' };

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
        <span className="text-xs font-medium text-zinc-300">{current.title}</span>
      </div>

      <div className="flex items-center gap-3">
        <Badge variant="ready">{current.phase}</Badge>
        <div className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" title="System Operational" />
      </div>
    </header>
  );
};
