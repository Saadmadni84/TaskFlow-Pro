import React from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';

export default function HomePage() {
  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Overview header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 1 Foundation
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Architecture & Local Infrastructure</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Deterministic DAG Scheduling Platform
        </h1>
        <p className="text-sm text-zinc-400 max-w-2xl leading-relaxed">
          TaskFlow Pro enforces dependency invariants at the backend domain layer.
          The deterministic graph scheduler owns readiness states, topological schedule propagation,
          and cycle prevention.
        </p>
      </div>

      {/* Architectural Pillars */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Core Principle</span>
            <Badge variant="neutral">Invariant</Badge>
          </div>
          <div className="text-sm font-medium text-zinc-200">
            Engine as Single Source of Truth
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            The frontend never calculates Ready/Blocked status or graph validity. All state transitions
            are validated server-side.
          </p>
        </Card>

        <Card className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Backend Monolith</span>
            <Badge variant="ready">Java 21 / Spring</Badge>
          </div>
          <div className="text-sm font-medium text-zinc-200">
            Domain-Oriented Modules
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Structured into task, dependency, scheduling, critical path, and AI modules with Flyway migrations.
          </p>
        </Card>

        <Card className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Local Infrastructure</span>
            <Badge variant="ready">Docker / Postgres</Badge>
          </div>
          <div className="text-sm font-medium text-zinc-200">
            PostgreSQL 16 Service
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Containerized persistence layer with reproducible environment variables and healthcheck automation.
          </p>
        </Card>
      </div>

      {/* Semantic State Foundations */}
      <Card className="space-y-4">
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <div>
            <h2 className="text-sm font-medium text-zinc-200">Semantic Dependency States</h2>
            <p className="text-xs text-zinc-400">Strictly defined visual states mapped to domain rules</p>
          </div>
          <span className="text-xs font-mono text-zinc-500">Design System</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-1">
          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-zinc-300 font-medium">Ready</span>
              <Badge variant="ready">Ready</Badge>
            </div>
            <p className="text-[11px] text-zinc-400 leading-normal">
              All predecessor dependencies satisfied. Task is eligible for execution.
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-zinc-300 font-medium">Blocked</span>
              <Badge variant="blocked">Blocked</Badge>
            </div>
            <p className="text-[11px] text-zinc-400 leading-normal">
              One or more upstream dependencies pending completion.
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-zinc-300 font-medium">Warning</span>
              <Badge variant="warning">Warning</Badge>
            </div>
            <p className="text-[11px] text-zinc-400 leading-normal">
              Schedule slip or impending deadline conflict requiring attention.
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-zinc-300 font-medium">Neutral</span>
              <Badge variant="neutral">Backlog</Badge>
            </div>
            <p className="text-[11px] text-zinc-400 leading-normal">
              Unscheduled or backlog state awaiting dependency definition.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}
