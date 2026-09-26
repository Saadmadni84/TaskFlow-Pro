import React from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';

export default function DomainModelPage() {
  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 2 Domain Model
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">PostgreSQL Persistence & Relational Invariants</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Core Domain Model & Persistence Layer
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          TaskFlow Pro models projects, tasks, and directed dependencies using JPA/Hibernate backed by PostgreSQL 16.
          All entities enforce strict database-level constraints, optimistic locking, and project scope isolation.
        </p>
      </div>

      {/* Domain Entities Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Entity</span>
            <Badge variant="ready">Project</Badge>
          </div>
          <div className="text-sm font-medium text-zinc-200">
            Project Root Aggregate
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Logical workspace isolating graphs and tasks. Provides boundary isolation ensuring dependencies never cross projects.
          </p>
          <div className="pt-2 text-[11px] font-mono text-zinc-500 border-t border-zinc-800/60">
            Table: <code className="text-zinc-300">projects</code> (UUID PK)
          </div>
        </Card>

        <Card className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Entity</span>
            <Badge variant="ready">Task</Badge>
          </div>
          <div className="text-sm font-medium text-zinc-200">
            Task with Dual States
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Decoupled workflow status (<code className="text-zinc-300">BACKLOG, IN_PROGRESS, REVIEW, DONE</code>) and derived dependency state (<code className="text-emerald-400">READY</code> / <code className="text-rose-400">BLOCKED</code>).
          </p>
          <div className="pt-2 text-[11px] font-mono text-zinc-500 border-t border-zinc-800/60">
            Table: <code className="text-zinc-300">tasks</code> (@Version optimistic locking)
          </div>
        </Card>

        <Card className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Entity</span>
            <Badge variant="ready">TaskDependency</Badge>
          </div>
          <div className="text-sm font-medium text-zinc-200">
            Directed Graph Edge
          </div>
          <p className="text-xs text-zinc-400 leading-relaxed">
            Represents <code className="text-zinc-300">predecessor_task_id → successor_task_id</code>. Enforces self-dependency prevention and unique directional edges.
          </p>
          <div className="pt-2 text-[11px] font-mono text-zinc-500 border-t border-zinc-800/60">
            Table: <code className="text-zinc-300">task_dependencies</code> (Composite unique)
          </div>
        </Card>
      </div>

      {/* Relational Schema Card */}
      <Card className="space-y-4">
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <div>
            <h2 className="text-sm font-semibold text-zinc-100">Relational Schema & Concurrency Invariants</h2>
            <p className="text-xs text-zinc-400">Enforced via Flyway SQL migrations and JPA/Hibernate domain mapping</p>
          </div>
          <span className="text-xs font-mono text-zinc-500">PostgreSQL 16 + JPA</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-1">
          <div className="p-3.5 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <span className="text-xs text-zinc-300 font-medium">UUID Primary Keys</span>
            <p className="text-[11px] text-zinc-400 leading-normal">
              PostgreSQL <code className="text-zinc-300">UUID PRIMARY KEY</code> avoids enumeration attacks and simplifies cross-environment identification.
            </p>
          </div>

          <div className="p-3.5 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <span className="text-xs text-zinc-300 font-medium">JPA Optimistic Locking</span>
            <p className="text-[11px] text-zinc-400 leading-normal">
              JPA/Hibernate <code className="text-zinc-300">@Version</code> on the database <code className="text-zinc-300">version BIGINT</code> column prevents lost updates and concurrent overwrite collisions.
            </p>
          </div>

          <div className="p-3.5 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <span className="text-xs text-zinc-300 font-medium">Database Constraints</span>
            <p className="text-[11px] text-zinc-400 leading-normal">
              PostgreSQL CHECK constraint ensures <code className="text-zinc-300">predecessor_task_id &lt;&gt; successor_task_id</code>, and UNIQUE constraint prevents duplicate edges.
            </p>
          </div>

          <div className="p-3.5 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <span className="text-xs text-zinc-300 font-medium">Cascade Protection</span>
            <p className="text-[11px] text-zinc-400 leading-normal">
              Foreign key <code className="text-zinc-300">ON DELETE CASCADE</code> guarantees deleting a task transactionally removes all incident dependency edges.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}
