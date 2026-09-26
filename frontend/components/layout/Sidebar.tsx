import React from 'react';

interface NavItem {
  name: string;
  badge?: string;
  active?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { name: 'Architecture', active: true },
  { name: 'DAG Schedule Engine', badge: 'Phase 2' },
  { name: 'Kanban Board', badge: 'Phase 3' },
  { name: 'Critical Path', badge: 'Phase 4' },
  { name: 'AI Suggestions', badge: 'Phase 5' },
];

export const Sidebar: React.FC = () => {
  return (
    <aside className="w-64 border-r border-zinc-800/80 bg-zinc-950/40 p-4 flex flex-col justify-between hidden md:flex min-h-[calc(100vh-3.5rem)]">
      <div className="space-y-1">
        <div className="px-2 py-1.5 text-[11px] font-mono font-medium uppercase tracking-wider text-zinc-500">
          Workspaces
        </div>
        <div className="space-y-1">
          {NAV_ITEMS.map((item) => (
            <div
              key={item.name}
              className={`flex items-center justify-between px-2.5 py-1.5 rounded-md text-xs font-medium cursor-pointer transition-colors ${
                item.active
                  ? 'bg-zinc-800/80 text-zinc-100'
                  : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/50'
              }`}
            >
              <span>{item.name}</span>
              {item.badge && (
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-zinc-800/80 text-zinc-500 font-mono">
                  {item.badge}
                </span>
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="p-3 rounded-lg border border-zinc-800/60 bg-zinc-900/40">
        <div className="text-[11px] font-mono uppercase text-zinc-500 mb-1">Architecture Principle</div>
        <p className="text-[11px] text-zinc-400 leading-relaxed">
          The deterministic DAG/dependency engine is the source of truth.
        </p>
      </div>
    </aside>
  );
};
