'use client';

import React from 'react';
import { ScheduleImpactPreviewResponse } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { formatCalendarDate } from '@/lib/utils/dates';

interface ScheduleImpactPreviewModalProps {
  isOpen: boolean;
  previewData: ScheduleImpactPreviewResponse | null;
  sourceTaskTitle: string;
  originalDate?: string;
  proposedDate: string;
  loading?: boolean;
  applying?: boolean;
  onCancel: () => void;
  onApply: () => Promise<void>;
}

export const ScheduleImpactPreviewModal: React.FC<ScheduleImpactPreviewModalProps> = ({
  isOpen,
  previewData,
  sourceTaskTitle,
  originalDate,
  proposedDate,
  loading = false,
  applying = false,
  onCancel,
  onApply,
}) => {
  if (!isOpen) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onCancel}
      title="Schedule Impact Preview"
      description="Review downstream schedule propagation before committing changes."
      maxWidth="xl"
    >
      <div className="space-y-4">
        {/* Source Change Summary */}
        <div className="p-3.5 rounded-lg bg-zinc-950/80 border border-zinc-800 space-y-2">
          <div className="text-xs font-mono uppercase text-zinc-400">Proposed Modification</div>
          <div className="flex items-center justify-between text-xs">
            <span className="font-semibold text-zinc-200">
              Moving &ldquo;{sourceTaskTitle}&rdquo;
            </span>
            <div className="flex items-center gap-2 font-mono">
              <span className="text-zinc-400">{formatCalendarDate(originalDate)}</span>
              <span className="text-zinc-600">→</span>
              <span className="text-amber-400 font-semibold">{formatCalendarDate(proposedDate)}</span>
            </div>
          </div>
        </div>

        {/* Loading State */}
        {loading && (
          <div className="py-12 flex flex-col items-center justify-center space-y-2 text-center text-xs text-zinc-400 font-mono">
            <div className="h-5 w-5 border-2 border-zinc-600 border-t-amber-400 rounded-full animate-spin" />
            <span>Calculating deterministic downstream impact...</span>
          </div>
        )}

        {/* Preview Results */}
        {!loading && previewData && (
          <div className="space-y-4">
            {/* Impact Metric Summary */}
            <div className="grid grid-cols-3 gap-2">
              <div className="p-2.5 rounded bg-zinc-950 border border-zinc-800/80 text-center">
                <div className="text-[10px] font-mono uppercase text-zinc-500">Affected Tasks</div>
                <div className="text-base font-bold text-zinc-200 font-mono mt-0.5">
                  {previewData.summary.affectedTaskCount}
                </div>
              </div>
              <div className="p-2.5 rounded bg-zinc-950 border border-zinc-800/80 text-center">
                <div className="text-[10px] font-mono uppercase text-zinc-500">Changed Schedule</div>
                <div className="text-base font-bold text-amber-400 font-mono mt-0.5">
                  {previewData.summary.changedTaskCount}
                </div>
              </div>
              <div className="p-2.5 rounded bg-zinc-950 border border-zinc-800/80 text-center">
                <div className="text-[10px] font-mono uppercase text-zinc-500">Max Delay</div>
                <div className="text-base font-bold text-rose-400 font-mono mt-0.5">
                  +{previewData.summary.maximumDelayDays}d
                </div>
              </div>
            </div>

            {/* Affected Tasks List */}
            <div className="space-y-1.5">
              <div className="text-xs font-mono uppercase text-zinc-400 flex items-center justify-between">
                <span>Affected Downstream Tasks</span>
                <span className="text-[10px] text-zinc-500">
                  {previewData.tasks.length} total in subgraph
                </span>
              </div>

              <div className="max-h-56 overflow-y-auto space-y-1.5 pr-1">
                {previewData.tasks.map((task) => {
                  const shift = task.shiftDays || task.startShiftDays || 0;
                  const hasShift = shift !== 0;

                  return (
                    <div
                      key={task.taskId}
                      className="p-2.5 rounded-lg bg-zinc-950/60 border border-zinc-800/60 flex flex-col gap-1 text-xs"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-medium text-zinc-200 truncate max-w-[280px]">
                          {task.title}
                        </span>
                        <span
                          className={`font-mono text-xs px-2 py-0.5 rounded font-semibold ${
                            hasShift
                              ? shift > 0
                                ? 'bg-rose-950/60 text-rose-400 border border-rose-800/40'
                                : 'bg-emerald-950/60 text-emerald-400 border border-emerald-800/40'
                              : 'bg-zinc-800 text-zinc-400'
                          }`}
                        >
                          {shift > 0 ? `+${shift} days` : shift < 0 ? `${shift} days` : '0 days'}
                        </span>
                      </div>

                      {/* Schedule Shift Details */}
                      <div className="flex items-center justify-between text-[11px] text-zinc-400 font-mono">
                        <span>
                          {formatCalendarDate(task.currentScheduledStart)} →{' '}
                          <span className="text-zinc-200 font-semibold">
                            {formatCalendarDate(task.proposedScheduledStart)}
                          </span>
                        </span>
                      </div>

                      {/* Constraint Explanation */}
                      {task.reason && (
                        <div className="text-[11px] text-zinc-400 bg-zinc-900/50 p-1.5 rounded border border-zinc-800/40 leading-snug">
                          {task.reason}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Converging Path Guidance */}
            <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800/80 text-xs text-zinc-400 leading-relaxed">
              <span className="text-zinc-300 font-semibold">Non-compounding schedule:</span> Delays along converging paths are governed by the latest prerequisite rather than compounding multiple times.
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="pt-3 border-t border-zinc-800/80 flex items-center justify-end gap-2">
          <Button variant="ghost" size="sm" type="button" onClick={onCancel} disabled={applying}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            type="button"
            onClick={onApply}
            disabled={loading || applying}
          >
            {applying ? 'Applying Change...' : 'Apply Schedule Change'}
          </Button>
        </div>
      </div>
    </Modal>
  );
};
