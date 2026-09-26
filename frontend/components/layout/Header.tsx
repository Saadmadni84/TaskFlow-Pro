'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const ROUTE_INFO: Record<string, { title: string; phase: string }> = {
  '/': { title: 'Architecture & Foundation', phase: 'Phase 1' },
  '/domain-model': { title: 'Domain Persistence & PostgreSQL', phase: 'Phase 2' },
  '/dag-engine': { title: 'Deterministic DAG Engine', phase: 'Phase 3' },
  '/readiness': { title: 'Dependency Readiness Engine', phase: 'Phase 4' },
  '/scheduling': { title: 'Dependency-Aware Scheduling Engine', phase: 'Phase 5' },
  '/dag-schedule-engine': { title: 'Dependency-Aware Scheduling Engine', phase: 'Phase 5' },
  '/impact-preview': { title: 'Dependency Impact Preview', phase: 'Phase 6' },
  '/ai-suggestions': { title: 'AI Dependency Suggestions', phase: 'Phase 7' },
  '/critical-path': { title: 'Critical Path Analysis', phase: 'Phase 8' },
  '/kanban': { title: 'Production Kanban Board', phase: 'Phase 9' },
  '/kanban-board': { title: 'Production Kanban Board', phase: 'Phase 9' },
  '/graph': { title: 'Visual DAG Graph', phase: 'Phase 9' },
};

export const Header: React.FC = () => {
  const pathname = usePathname();
  const current =
    ROUTE_INFO[pathname] ||
    (pathname.startsWith('/projects')
      ? { title: 'Project Workflow Workspace', phase: 'Phase 9' }
      : { title: 'DAG Scheduling Platform', phase: 'System' });

  return (
    <header className="h-14 border-b border-zinc-800/80 bg-zinc-950/80 backdrop-blur-md px-4 sm:px-6 flex items-center justify-between sticky top-0 z-20 select-none">
      {/* Left / Contextual Breadcrumb Area */}
      <nav aria-label="Breadcrumb" className="flex items-center gap-2 sm:gap-2.5 min-w-0">
        {/* Clickable Brand Home Link */}
        <Link
          href="/"
          aria-label="TaskFlow Pro home"
          className="group inline-flex items-center gap-2 sm:gap-2.5 rounded-md px-1.5 py-1 -ml-1.5 text-zinc-100 transition-colors duration-150 hover:bg-zinc-900/80 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-emerald-500/80 focus-visible:ring-offset-1 focus-visible:ring-offset-zinc-950 cursor-pointer shrink-0"
        >
          {/* Logo icon */}
          <div className="h-5 w-5 rounded bg-zinc-100 flex items-center justify-center shrink-0 transition-transform duration-150 group-hover:scale-105">
            <div className="h-2.5 w-2.5 rounded-sm bg-zinc-950 rotate-45" />
          </div>

          {/* Brand Typography */}
          <span className="font-semibold text-sm tracking-tight text-zinc-100 group-hover:text-white transition-colors duration-150">
            TaskFlow <span className="text-zinc-500 font-normal group-hover:text-zinc-400 transition-colors duration-150">Pro</span>
          </span>
        </Link>

        {/* Breadcrumb Separator & Current Page Context */}
        {current.title && (
          <div className="flex items-center gap-2 sm:gap-2.5 min-w-0">
            <span className="text-xs text-zinc-600 select-none shrink-0" aria-hidden="true">
              /
            </span>
            <span
              className="text-xs font-medium text-zinc-400 truncate max-w-[140px] xs:max-w-[200px] sm:max-w-[320px] md:max-w-[420px]"
              title={current.title}
            >
              {current.title}
            </span>
          </div>
        )}
      </nav>

      {/* Right / Secondary Phase Indicator */}
      <div className="flex items-center gap-3 shrink-0 ml-3">
        <div
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-mono font-medium tracking-wider uppercase bg-zinc-900/90 border border-zinc-800 text-zinc-400 shadow-sm"
          title={`Active Architectural Engine: ${current.phase}`}
        >
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 shrink-0" aria-hidden="true" />
          <span>{current.phase}</span>
        </div>
      </div>
    </header>
  );
};

