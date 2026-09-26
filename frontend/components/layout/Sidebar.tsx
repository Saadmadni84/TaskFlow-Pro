'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

interface NavItem {
  name: string;
  href: string;
  badge?: string;
  badgeVariant?: 'implemented' | 'planned';
}

const PRIMARY_WORKSPACE: NavItem[] = [
  { name: 'Kanban Board', href: '/kanban', badge: 'Phase 9', badgeVariant: 'implemented' },
];

const ARCHITECTURE_PHASES: NavItem[] = [
  { name: 'Architecture & Foundation', href: '/', badge: 'Phase 1', badgeVariant: 'implemented' },
  { name: 'Domain Persistence', href: '/domain-model', badge: 'Phase 2', badgeVariant: 'implemented' },
  { name: 'Deterministic DAG Engine', href: '/dag-engine', badge: 'Phase 3', badgeVariant: 'implemented' },
  { name: 'Dependency Readiness', href: '/readiness', badge: 'Phase 4', badgeVariant: 'implemented' },
  { name: 'Scheduling Engine', href: '/scheduling', badge: 'Phase 5', badgeVariant: 'implemented' },
  { name: 'Impact Preview', href: '/impact-preview', badge: 'Phase 6', badgeVariant: 'implemented' },
  { name: 'AI Suggestions', href: '/ai-suggestions', badge: 'Phase 7', badgeVariant: 'implemented' },
  { name: 'Critical Path Analysis', href: '/critical-path', badge: 'Phase 8', badgeVariant: 'implemented' },
];

export const Sidebar: React.FC = () => {
  const pathname = usePathname();

  const isActive = (href: string) => {
    if (href === '/') {
      return pathname === '/';
    }
    return pathname.startsWith(href);
  };

  return (
    <aside className="w-64 border-r border-zinc-800/80 bg-zinc-950/40 p-4 flex flex-col justify-between hidden md:flex min-h-[calc(100vh-3.5rem)] select-none">
      <div className="space-y-5">
        {/* Production Workspace */}
        <div className="space-y-1">
          <div className="flex items-center justify-between px-2 py-1 text-[11px] font-mono font-medium uppercase tracking-wider text-zinc-500">
            <span>Workspace</span>
            <span className="text-[10px] text-emerald-400 font-normal">Production</span>
          </div>
          <div className="space-y-0.5">
            {PRIMARY_WORKSPACE.map((item) => {
              const active = isActive(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center justify-between px-2.5 py-2.5 rounded-lg text-xs font-semibold transition-all ${
                    active
                      ? 'bg-zinc-800 text-zinc-100 shadow-sm border border-emerald-500/40'
                      : 'text-zinc-300 hover:text-zinc-100 hover:bg-zinc-900/80 border border-transparent'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="h-2 w-2 rounded-full bg-emerald-400" />
                    <span>{item.name}</span>
                  </div>
                  {item.badge && (
                    <span className="text-[10px] px-1.5 py-0.5 rounded font-mono bg-emerald-950/80 border border-emerald-500/40 text-emerald-400">
                      {item.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        </div>

        {/* Core Implemented Engine Demonstrators */}
        <div className="space-y-1">
          <div className="flex items-center justify-between px-2 py-1 text-[11px] font-mono font-medium uppercase tracking-wider text-zinc-500">
            <span>Architectural Engines</span>
            <span className="text-[10px] text-zinc-500 font-normal">Phases 1–8</span>
          </div>
          <div className="space-y-0.5 max-h-[46vh] overflow-y-auto pr-0.5">
            {ARCHITECTURE_PHASES.map((item) => {
              const active = isActive(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center justify-between px-2.5 py-1.5 rounded-md text-xs font-medium transition-all ${
                    active
                      ? 'bg-zinc-800/90 text-zinc-100 shadow-sm border border-zinc-700/50'
                      : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/60'
                  }`}
                >
                  <span className="truncate">{item.name}</span>
                  {item.badge && (
                    <span
                      className={`text-[10px] px-1.5 py-0.5 rounded font-mono shrink-0 ml-1.5 ${
                        active
                          ? 'bg-emerald-950/80 border border-emerald-500/40 text-emerald-400'
                          : 'bg-zinc-800/80 text-zinc-400 border border-zinc-700/30'
                      }`}
                    >
                      {item.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        </div>
      </div>

      <div className="p-3 rounded-lg border border-zinc-800/60 bg-zinc-900/40 mt-4">
        <div className="text-[11px] font-mono uppercase text-zinc-400 mb-1 flex items-center justify-between">
          <span>DAG Engine</span>
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400"></span>
        </div>
        <p className="text-[11px] text-zinc-400 leading-relaxed">
          The deterministic DAG scheduling engine is the single source of truth.
        </p>
      </div>
    </aside>
  );
};
