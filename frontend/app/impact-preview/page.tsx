'use client';

import React, { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

interface PreviewTask {
  taskId: string;
  title: string;
  currentPlannedStart: string;
  proposedPlannedStart: string;
  currentScheduledStart: string;
  proposedScheduledStart: string;
  currentScheduledDue: string;
  proposedScheduledDue: string;
  durationDays: number;
  shiftDays: number;
  impactType: 'DELAYED' | 'CONSTRAINT_DRIVEN' | 'UNCHANGED' | 'MOVED_EARLIER';
  reasonType: 'SOURCE_TASK_CHANGE' | 'DEPENDENCY_CONSTRAINT' | 'PLANNED_DATE_DOMINANT' | 'NO_CHANGE';
  constraintSourceTaskIds: string[];
  reason: string;
}

export default function ImpactPreviewPage() {
  const [selectedShiftDays, setSelectedShiftDays] = useState<number>(3);

  // Simulated Diamond Graph: A -> B -> D and A -> C -> D
  // Base A: 2026-06-01 -> 2026-06-03 (3d)
  // Base B: 2026-06-04 -> 2026-06-05 (2d)
  // Base C: 2026-06-04 -> 2026-06-05 (2d)
  // Base D: 2026-06-06 -> 2026-06-07 (2d)
  const shift = selectedShiftDays;

  const padDay = (day: number) => (day < 10 ? `0${day}` : `${day}`);

  const startA = 1 + shift;
  const dueA = startA + 2;

  const startB = dueA + 1;
  const dueB = startB + 1;

  const startC = dueA + 1;
  const dueC = startC + 1;

  const startD = Math.max(dueB, dueC) + 1;
  const dueD = startD + 1;

  const previewTasks: PreviewTask[] = [
    {
      taskId: 'task-a',
      title: 'Task A (Database Schema)',
      currentPlannedStart: '2026-06-01',
      proposedPlannedStart: `2026-06-${padDay(startA)}`,
      currentScheduledStart: '2026-06-01',
      proposedScheduledStart: `2026-06-${padDay(startA)}`,
      currentScheduledDue: '2026-06-03',
      proposedScheduledDue: `2026-06-${padDay(dueA)}`,
      durationDays: 3,
      shiftDays: shift,
      impactType: shift === 0 ? 'UNCHANGED' : shift > 0 ? 'DELAYED' : 'MOVED_EARLIER',
      reasonType: 'SOURCE_TASK_CHANGE',
      constraintSourceTaskIds: [],
      reason:
        shift === 0
          ? 'Proposed planned start date matches current planned start date; no schedule change.'
          : `Source task planned start moved later by ${shift} days.`,
    },
    {
      taskId: 'task-b',
      title: 'Task B (Backend API)',
      currentPlannedStart: '2026-06-04',
      proposedPlannedStart: '2026-06-04',
      currentScheduledStart: '2026-06-04',
      proposedScheduledStart: `2026-06-${padDay(startB)}`,
      currentScheduledDue: '2026-06-05',
      proposedScheduledDue: `2026-06-${padDay(dueB)}`,
      durationDays: 2,
      shiftDays: shift,
      impactType: shift === 0 ? 'UNCHANGED' : 'CONSTRAINT_DRIVEN',
      reasonType: shift === 0 ? 'PLANNED_DATE_DOMINANT' : 'DEPENDENCY_CONSTRAINT',
      constraintSourceTaskIds: ['Task A'],
      reason:
        shift === 0
          ? 'Task B planned schedule is already later than or equal to predecessor constraint.'
          : `Task B must start on or after 2026-06-${padDay(startB)}, constrained by predecessor [Task A] due on 2026-06-${padDay(dueA)}.`,
    },
    {
      taskId: 'task-c',
      title: 'Task C (Data Migration)',
      currentPlannedStart: '2026-06-04',
      proposedPlannedStart: '2026-06-04',
      currentScheduledStart: '2026-06-04',
      proposedScheduledStart: `2026-06-${padDay(startC)}`,
      currentScheduledDue: '2026-06-05',
      proposedScheduledDue: `2026-06-${padDay(dueC)}`,
      durationDays: 2,
      shiftDays: shift,
      impactType: shift === 0 ? 'UNCHANGED' : 'CONSTRAINT_DRIVEN',
      reasonType: shift === 0 ? 'PLANNED_DATE_DOMINANT' : 'DEPENDENCY_CONSTRAINT',
      constraintSourceTaskIds: ['Task A'],
      reason:
        shift === 0
          ? 'Task C planned schedule is already later than or equal to predecessor constraint.'
          : `Task C must start on or after 2026-06-${padDay(startC)}, constrained by predecessor [Task A] due on 2026-06-${padDay(dueA)}.`,
    },
    {
      taskId: 'task-d',
      title: 'Task D (Integration Tests)',
      currentPlannedStart: '2026-06-06',
      proposedPlannedStart: '2026-06-06',
      currentScheduledStart: '2026-06-06',
      proposedScheduledStart: `2026-06-${padDay(startD)}`,
      currentScheduledDue: '2026-06-07',
      proposedScheduledDue: `2026-06-${padDay(dueD)}`,
      durationDays: 2,
      shiftDays: shift,
      impactType: shift === 0 ? 'UNCHANGED' : 'CONSTRAINT_DRIVEN',
      reasonType: 'DEPENDENCY_CONSTRAINT',
      constraintSourceTaskIds: ['Task B', 'Task C'],
      reason: `Task D is constrained by multiple predecessors (Task B, Task C) finishing on the same date. Non-compounding scheduling applies; earliest allowed start is 2026-06-${padDay(startD)}.`,
    },
  ];

  const changedCount = previewTasks.filter((t) => t.shiftDays !== 0).length;

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 6 Impact Preview
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Deterministic Side-Effect-Free Simulation</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Dependency Impact Preview Simulator
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          Before committing a schedule mutation, TaskFlow Pro runs a side-effect-free in-memory simulation
          using the authoritative Phase 5 calculation engine. It predicts downstream schedule changes,
          identifies binding predecessors, and explains exactly why shifts occur.
        </p>
      </div>

      {/* Control Panel Card */}
      <Card className="space-y-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 border-b border-zinc-800/80 pb-4">
          <div>
            <h2 className="text-sm font-medium text-zinc-200">Interactive Propose Schedule Change</h2>
            <p className="text-xs text-zinc-400">Propose moving Task A by varying days and inspect the predicted impact</p>
          </div>

          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant={selectedShiftDays === 0 ? 'secondary' : 'outline'}
              onClick={() => setSelectedShiftDays(0)}
            >
              Same Date (+0d)
            </Button>
            <Button
              size="sm"
              variant={selectedShiftDays === 3 ? 'secondary' : 'outline'}
              onClick={() => setSelectedShiftDays(3)}
            >
              Propose +3 Days
            </Button>
            <Button
              size="sm"
              variant={selectedShiftDays === 5 ? 'secondary' : 'outline'}
              onClick={() => setSelectedShiftDays(5)}
            >
              Propose +5 Days
            </Button>
          </div>
        </div>

        {/* Summary Metrics */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 pt-1">
          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/40">
            <div className="text-[11px] font-mono text-zinc-400">Affected Tasks</div>
            <div className="text-xl font-semibold text-zinc-100 mt-1">{previewTasks.length}</div>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/40">
            <div className="text-[11px] font-mono text-zinc-400">Changed Tasks</div>
            <div className="text-xl font-semibold text-amber-400 mt-1">{changedCount}</div>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/40">
            <div className="text-[11px] font-mono text-zinc-400">Unchanged Tasks</div>
            <div className="text-xl font-semibold text-zinc-300 mt-1">
              {previewTasks.length - changedCount}
            </div>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/40">
            <div className="text-[11px] font-mono text-zinc-400">Max Delay Days</div>
            <div className="text-xl font-semibold text-emerald-400 mt-1">+{shift} days</div>
          </div>
        </div>
      </Card>

      {/* Task Impact Comparison Table */}
      <Card className="space-y-4">
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <h2 className="text-sm font-medium text-zinc-200">Simulated Schedule Changes</h2>
          <span className="text-xs font-mono text-zinc-500">POST /api/tasks/&#123;id&#125;/schedule/preview</span>
        </div>

        <div className="space-y-3">
          {previewTasks.map((t) => (
            <div
              key={t.taskId}
              className="p-4 rounded-lg border border-zinc-800/80 bg-zinc-950/40 space-y-2.5 transition-all"
            >
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-zinc-100">{t.title}</span>
                  <Badge
                    variant={
                      t.impactType === 'UNCHANGED'
                        ? 'neutral'
                        : t.impactType === 'CONSTRAINT_DRIVEN'
                        ? 'warning'
                        : 'ready'
                    }
                  >
                    {t.impactType}
                  </Badge>
                </div>

                <div className="text-xs font-mono flex items-center gap-2">
                  <span className="text-zinc-500">Shift:</span>
                  <span
                    className={
                      t.shiftDays > 0
                        ? 'text-amber-400 font-semibold'
                        : 'text-zinc-400'
                    }
                  >
                    {t.shiftDays > 0 ? `+${t.shiftDays} days` : '0 days'}
                  </span>
                </div>
              </div>

              {/* Date Comparison */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                <div className="p-2 rounded bg-zinc-900/60 border border-zinc-800/40 text-zinc-400">
                  <span className="text-[10px] font-mono uppercase text-zinc-500 block mb-0.5">Current Schedule</span>
                  <span className="text-zinc-300 font-medium">
                    {t.currentScheduledStart} → {t.currentScheduledDue}
                  </span>
                  <span className="text-zinc-500 ml-2">({t.durationDays}d)</span>
                </div>

                <div className="p-2 rounded bg-zinc-900/60 border border-zinc-800/40 text-zinc-400">
                  <span className="text-[10px] font-mono uppercase text-emerald-500 block mb-0.5">Proposed Schedule</span>
                  <span className="text-zinc-100 font-medium">
                    {t.proposedScheduledStart} → {t.proposedScheduledDue}
                  </span>
                  <span className="text-zinc-500 ml-2">({t.durationDays}d)</span>
                </div>
              </div>

              {/* Reason Explanation */}
              <div className="pt-2 border-t border-zinc-800/50 flex flex-col sm:flex-row items-start sm:items-center justify-between text-xs gap-1">
                <p className="text-zinc-400 leading-relaxed">
                  <strong className="text-zinc-300 font-medium">Explanation: </strong>
                  {t.reason}
                </p>
                {t.constraintSourceTaskIds.length > 0 && (
                  <span className="text-[11px] font-mono text-zinc-500 shrink-0">
                    Binding: [{t.constraintSourceTaskIds.join(', ')}]
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
