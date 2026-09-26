'use client';

import React, { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

export default function DagEnginePage() {
  const [cycleAttempt, setCycleAttempt] = useState<string | null>(null);

  const handleTestCycle = () => {
    // Attempting to add D -> A creates a cycle: A -> B -> D -> A
    setCycleAttempt('Cycle detected: D → A would form closed cycle [A → B → D → A]. Edge rejected with HTTP 422 Unprocessable Entity.');
  };

  const handleClearCycle = () => {
    setCycleAttempt(null);
  };

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 3 DAG Engine
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Deterministic Directed Acyclic Graph</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Deterministic DAG & Cycle Prevention Engine
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          The TaskFlow Pro DAG engine represents task dependencies as a mathematical directed graph.
          Every dependency addition executes a transactional DFS reachability analysis to guarantee zero cycles,
          and Kahn&apos;s algorithm produces deterministic topological orderings with stable tie-breaking.
        </p>
      </div>

      {/* Interactive Visual Graph Card */}
      <Card className="space-y-6">
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <div>
            <h2 className="text-sm font-medium text-zinc-200">Interactive DAG Graph Explorer</h2>
            <p className="text-xs text-zinc-400">Diamond dependency graph with converging paths: A → B, A → C, B → D, C → D</p>
          </div>
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" onClick={handleTestCycle}>
              Test Cycle (D → A)
            </Button>
            {cycleAttempt && (
              <Button size="sm" variant="outline" onClick={handleClearCycle}>
                Reset
              </Button>
            )}
          </div>
        </div>

        {/* Visual Nodes and Arrows */}
        <div className="p-6 rounded-lg bg-zinc-950/60 border border-zinc-800/80 flex flex-col md:flex-row items-center justify-around gap-6">
          <div className="p-4 rounded-lg bg-zinc-900 border border-zinc-700/60 text-center space-y-1 w-44 shadow-lg">
            <Badge variant="ready">Root Node</Badge>
            <div className="text-sm font-semibold text-zinc-100">Task A</div>
            <p className="text-[11px] text-zinc-400">Database Schema</p>
            <div className="text-[10px] font-mono text-zinc-500 pt-1">In-degree: 0 | Out: 2</div>
          </div>

          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-lg bg-zinc-900 border border-zinc-800 text-center space-y-1 w-44 shadow-md">
              <Badge variant="neutral">Branch 1</Badge>
              <div className="text-sm font-semibold text-zinc-200">Task B</div>
              <p className="text-[11px] text-zinc-400">Backend API</p>
              <div className="text-[10px] font-mono text-zinc-500 pt-1">In-degree: 1 | Out: 1</div>
            </div>

            <div className="p-4 rounded-lg bg-zinc-900 border border-zinc-800 text-center space-y-1 w-44 shadow-md">
              <Badge variant="neutral">Branch 2</Badge>
              <div className="text-sm font-semibold text-zinc-200">Task C</div>
              <p className="text-[11px] text-zinc-400">Data Migration</p>
              <div className="text-[10px] font-mono text-zinc-500 pt-1">In-degree: 1 | Out: 1</div>
            </div>
          </div>

          <div className="p-4 rounded-lg bg-zinc-900 border border-zinc-700/60 text-center space-y-1 w-44 shadow-lg">
            <Badge variant="blocked">Converging Node</Badge>
            <div className="text-sm font-semibold text-zinc-100">Task D</div>
            <p className="text-[11px] text-zinc-400">Integration Tests</p>
            <div className="text-[10px] font-mono text-zinc-500 pt-1">In-degree: 2 | Out: 0</div>
          </div>
        </div>

        {/* Cycle Detection Feedback */}
        {cycleAttempt && (
          <div className="p-3.5 rounded-lg border border-rose-500/40 bg-rose-950/40 text-rose-300 text-xs flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-rose-400 animate-ping" />
              <span>{cycleAttempt}</span>
            </div>
            <Badge variant="blocked">CYCLE_DETECTED</Badge>
          </div>
        )}

        {/* Algorithm Guarantees */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2">
          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">DFS Reachability</span>
            <p className="text-[11px] text-zinc-400">
              Evaluates if <code className="text-zinc-300">target</code> can reach <code className="text-zinc-300">source</code> before edge insertion in O(V + E).
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">Kahn&apos;s Topological Sort</span>
            <p className="text-[11px] text-zinc-400">
              Resolves tasks in valid dependency order with UUID deterministic tie-breaking for reproducible schedules.
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">Affected Subgraph</span>
            <p className="text-[11px] text-zinc-400">
              Extracts only downstream descendants for recalculation, ignoring unrelated project nodes.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}
