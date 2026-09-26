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

        <div className="pt-2 flex items-center gap-3">
          <a
            href="/kanban"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold text-xs shadow-md transition-all duration-150"
          >
            <span>Launch Kanban Board (Phase 9)</span>
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </a>
        </div>
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

      {/* Explicit State Taxonomy: Dependency vs Workflow */}
      <Card className="space-y-6">
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <div>
            <h2 className="text-sm font-semibold text-zinc-100">Domain State Taxonomy</h2>
            <p className="text-xs text-zinc-400">Strict architectural separation between derived dependency readiness and user workflow progression</p>
          </div>
          <span className="text-xs font-mono text-zinc-500">Phase 1 Foundations</span>
        </div>

        {/* 1. Dependency States */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-mono font-semibold uppercase tracking-wider text-emerald-400">
              Dependency State (Derived & System-Controlled)
            </h3>
            <span className="text-[10px] font-mono text-zinc-500">Authoritative DAG Engine</span>
          </div>
          <p className="text-xs text-zinc-400">
            Computed strictly on the backend from predecessor workflow completion. The frontend never calculates readiness.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
            <div className="p-3 rounded-lg border border-emerald-500/30 bg-emerald-950/20 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs text-emerald-300 font-semibold font-mono">READY</span>
                <Badge variant="ready">Ready</Badge>
              </div>
              <p className="text-[11px] text-zinc-400 leading-normal">
                All predecessor dependencies satisfied (<code className="text-emerald-400">workflowStatus == DONE</code>). Task is unblocked and eligible for execution.
              </p>
            </div>

            <div className="p-3 rounded-lg border border-rose-500/30 bg-rose-950/20 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs text-rose-300 font-semibold font-mono">BLOCKED</span>
                <Badge variant="blocked">Blocked</Badge>
              </div>
              <p className="text-[11px] text-zinc-400 leading-normal">
                One or more upstream prerequisite dependencies pending completion. Task execution cannot begin.
              </p>
            </div>
          </div>
        </div>

        {/* 2. Workflow States */}
        <div className="space-y-2 pt-2 border-t border-zinc-800/80">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-mono font-semibold uppercase tracking-wider text-sky-400">
              Workflow State (User-Controlled Lifecycle)
            </h3>
            <span className="text-[10px] font-mono text-zinc-500">Production Kanban Columns</span>
          </div>
          <p className="text-xs text-zinc-400">
            Represents user execution lifecycle across Kanban board columns.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-1">
            <div className="p-3 rounded-lg border border-zinc-800/60 bg-zinc-950/40 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs text-zinc-200 font-medium font-mono">BACKLOG</span>
                <Badge variant="neutral">Backlog</Badge>
              </div>
              <p className="text-[11px] text-zinc-400 leading-normal">
                Initial queued state awaiting prioritization and sprint planning.
              </p>
            </div>

            <div className="p-3 rounded-lg border border-blue-500/30 bg-blue-950/20 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs text-blue-300 font-medium font-mono">IN_PROGRESS</span>
                <Badge variant="ready">In Progress</Badge>
              </div>
              <p className="text-[11px] text-zinc-400 leading-normal">
                Actively being worked on by engineers or automation pipelines.
              </p>
            </div>

            <div className="p-3 rounded-lg border border-purple-500/30 bg-purple-950/20 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs text-purple-300 font-medium font-mono">REVIEW</span>
                <Badge variant="warning">Review</Badge>
              </div>
              <p className="text-[11px] text-zinc-400 leading-normal">
                Implementation complete; awaiting QA, code review, or sign-off.
              </p>
            </div>

            <div className="p-3 rounded-lg border border-emerald-500/30 bg-emerald-950/20 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs text-emerald-300 font-medium font-mono">DONE</span>
                <Badge variant="ready">Done</Badge>
              </div>
              <p className="text-[11px] text-zinc-400 leading-normal">
                Finished; satisfies downstream prerequisite constraints in the DAG.
              </p>
            </div>
          </div>
        </div>

        {/* 3. Visual Scheduling Indicator Note */}
        <div className="p-3 rounded-lg border border-amber-500/30 bg-amber-950/20 flex items-start gap-3 text-xs">
          <Badge variant="warning" className="shrink-0 mt-0.5">Warning</Badge>
          <div className="space-y-0.5 text-zinc-300">
            <span className="font-semibold text-amber-300">Visual Scheduling Warning (UI Indicator Only)</span>
            <p className="text-[11px] text-zinc-400">
              Visual warnings highlight schedule slips, impending deadline conflicts, or critical-path bottlenecks. Warning is purely a visual UI affordance, <strong>not</strong> a domain dependency state.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}
