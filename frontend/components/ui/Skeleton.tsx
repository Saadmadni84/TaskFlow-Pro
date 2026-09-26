'use client';

import React from 'react';

export const BoardSkeleton: React.FC = () => {
  return (
    <div className="w-full grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 animate-pulse">
      {[1, 2, 3, 4].map((col) => (
        <div
          key={col}
          className="rounded-xl bg-zinc-950/40 border border-zinc-800/60 p-3.5 space-y-3 min-h-[400px]"
        >
          {/* Header */}
          <div className="flex items-center justify-between pb-3 border-b border-zinc-800/40">
            <div className="h-4 w-24 bg-zinc-800 rounded" />
            <div className="h-4 w-6 bg-zinc-800 rounded-full" />
          </div>

          {/* Cards */}
          <div className="space-y-3">
            {[1, 2].map((card) => (
              <div
                key={card}
                className="p-4 rounded-lg bg-zinc-900/60 border border-zinc-800/60 space-y-2.5"
              >
                <div className="flex justify-between items-center">
                  <div className="h-3 w-16 bg-zinc-800 rounded-full" />
                  <div className="h-3 w-4 bg-zinc-800 rounded" />
                </div>
                <div className="h-4 w-3/4 bg-zinc-800 rounded" />
                <div className="h-3 w-1/2 bg-zinc-800/60 rounded" />
                <div className="pt-2 border-t border-zinc-800/40 flex justify-between">
                  <div className="h-2.5 w-16 bg-zinc-800/40 rounded" />
                  <div className="h-2.5 w-24 bg-zinc-800/40 rounded" />
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
};
