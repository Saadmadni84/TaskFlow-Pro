'use client';

import React, { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

export default function SchedulingPage() {
  const [shiftA, setShiftA] = useState<number>(0);

  const baseDateA = 1; // June 1
  const durationA = 3; // June 1 - 3
  const currentStartA = baseDateA + shiftA;
  const currentDueA = currentStartA + durationA - 1;

  // B depends on A (duration 2)
  const currentStartB = currentDueA + 1;
  const currentDueB = currentStartB + 2 - 1;

  // C depends on A (duration 2)
  const currentStartC = currentDueA + 1;
  const currentDueC = currentStartC + 2 - 1;

  // D depends on both B and C (duration 2)
  // Non-compounding: max(B.due + 1, C.due + 1)
  const currentStartD = Math.max(currentDueB, currentDueC) + 1;
  const currentDueD = currentStartD + 2 - 1;

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 5 Scheduling Engine
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Deterministic Constraint-Based Scheduling</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Dependency-Aware Scheduling Engine
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          TaskFlow Pro enforces mathematical constraint-based schedule recalculation.
          Tasks maintain independent <code className="text-zinc-200">plannedStartDate</code> baselines, duration is strictly preserved,
          and downstream delays propagate in topological order without compounding across converging graph paths.
        </p>
      </div>

      {/* Interactive Diamond Graph Simulation Card */}
      <Card className="space-y-6">
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <div>
            <h2 className="text-sm font-medium text-zinc-200">Non-Compounding Converging Path Simulator</h2>
            <p className="text-xs text-zinc-400">Graph: A → B → D and A → C → D. Shift Task A and observe how Task D receives +3 days, NOT +6 days.</p>
          </div>
          <div className="flex items-center gap-2">
            <Button size="sm" variant="secondary" onClick={() => setShiftA((prev) => prev + 3)}>
              Shift A (+3 Days)
            </Button>
            <Button size="sm" variant="outline" onClick={() => setShiftA(0)}>
              Reset Schedule
            </Button>
          </div>
        </div>

        {/* Schedule Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Task A */}
          <div className="p-4 rounded-lg border border-zinc-700/80 bg-zinc-900/60 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-mono text-zinc-400">Source Task</span>
              <Badge variant={shiftA > 0 ? 'warning' : 'neutral'}>
                {shiftA > 0 ? `+${shiftA}d Shift` : 'Baseline'}
              </Badge>
            </div>
            <div className="text-sm font-semibold text-zinc-100">Task A</div>
            <div className="text-xs text-zinc-400 space-y-1">
              <div>Scheduled: <strong className="text-zinc-200">June {currentStartA} → June {currentDueA}</strong></div>
              <div>Duration: <span className="font-mono text-zinc-300">3 days</span></div>
            </div>
            <div className="text-[11px] text-zinc-500 pt-2 border-t border-zinc-800">
              User planned start: June {currentStartA}
            </div>
          </div>

          {/* Task B */}
          <div className="p-4 rounded-lg border border-zinc-800/80 bg-zinc-950/60 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-mono text-zinc-400">Successor 1</span>
              <Badge variant={shiftA > 0 ? 'warning' : 'neutral'}>
                {shiftA > 0 ? `+${shiftA}d Constraint` : 'Baseline'}
              </Badge>
            </div>
            <div className="text-sm font-semibold text-zinc-200">Task B</div>
            <div className="text-xs text-zinc-400 space-y-1">
              <div>Scheduled: <strong className="text-zinc-200">June {currentStartB} → June {currentDueB}</strong></div>
              <div>Constraint: <span className="font-mono text-zinc-300">A.due + 1</span></div>
            </div>
            <div className="text-[11px] text-zinc-500 pt-2 border-t border-zinc-800">
              Shifted by +{shiftA} days
            </div>
          </div>

          {/* Task C */}
          <div className="p-4 rounded-lg border border-zinc-800/80 bg-zinc-950/60 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-mono text-zinc-400">Successor 2</span>
              <Badge variant={shiftA > 0 ? 'warning' : 'neutral'}>
                {shiftA > 0 ? `+${shiftA}d Constraint` : 'Baseline'}
              </Badge>
            </div>
            <div className="text-sm font-semibold text-zinc-200">Task C</div>
            <div className="text-xs text-zinc-400 space-y-1">
              <div>Scheduled: <strong className="text-zinc-200">June {currentStartC} → June {currentDueC}</strong></div>
              <div>Constraint: <span className="font-mono text-zinc-300">A.due + 1</span></div>
            </div>
            <div className="text-[11px] text-zinc-500 pt-2 border-t border-zinc-800">
              Shifted by +{shiftA} days
            </div>
          </div>

          {/* Task D */}
          <div className="p-4 rounded-lg border border-emerald-500/40 bg-emerald-950/20 space-y-3 shadow-md">
            <div className="flex items-center justify-between">
              <span className="text-xs font-mono text-emerald-400">Converging Node</span>
              <Badge variant="ready">
                {shiftA > 0 ? `+${shiftA}d (No compounding)` : 'Baseline'}
              </Badge>
            </div>
            <div className="text-sm font-semibold text-zinc-100">Task D</div>
            <div className="text-xs text-zinc-300 space-y-1">
              <div>Scheduled: <strong className="text-emerald-300">June {currentStartD} → June {currentDueD}</strong></div>
              <div>Constraint: <span className="font-mono text-zinc-200">max(B.due+1, C.due+1)</span></div>
            </div>
            <div className="text-[11px] text-emerald-400/80 pt-2 border-t border-emerald-500/20">
              Shift is +{shiftA}d (NOT +{shiftA * 2}d!)
            </div>
          </div>
        </div>

        {/* Technical Callout */}
        <div className="p-4 rounded-lg bg-zinc-900/60 border border-zinc-800 text-xs text-zinc-300 leading-relaxed space-y-1">
          <strong className="text-zinc-100 font-medium">Why This Matters:</strong>
          <p>
            In poorly designed scheduling systems, converging dependencies add delays from every path (+3 from B and +3 from C = +6).
            TaskFlow Pro enforces strict mathematical constraint scheduling: <code className="text-emerald-400">scheduledStartDate = max(plannedStartDate, max(predDue + 1))</code>.
            Because both B and C finish on the same day, D is constrained by their shared completion date, shifting by exactly +{shiftA} days.
          </p>
        </div>
      </Card>
    </div>
  );
}
