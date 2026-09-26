'use client';

import React, { useState, useEffect } from 'react';
import { CriticalPathResponse } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { formatCalendarDate } from '@/lib/utils/dates';

interface CriticalPathModalProps {
  isOpen: boolean;
  projectName: string;
  onClose: () => void;
  fetchCriticalPath: () => Promise<CriticalPathResponse | null>;
}

export const CriticalPathModal: React.FC<CriticalPathModalProps> = ({
  isOpen,
  projectName,
  onClose,
  fetchCriticalPath,
}) => {
  const [data, setData] = useState<CriticalPathResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setData(null);
      setErrorMsg(null);
      return;
    }

    const loadData = async () => {
      setLoading(true);
      setErrorMsg(null);
      try {
        const result = await fetchCriticalPath();
        setData(result);
      } catch (err: unknown) {
        setErrorMsg(err instanceof Error ? err.message : 'Failed to load critical path analysis');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [isOpen, fetchCriticalPath]);

  // Map task ID to task metrics
  const taskMap = React.useMemo(() => {
    const map = new Map<string, { title: string; isCritical: boolean; totalSlackDays: number }>();
    if (data?.tasks) {
      for (const t of data.tasks) {
        map.set(t.taskId, {
          title: t.title,
          isCritical: t.isCritical,
          totalSlackDays: t.totalSlackDays,
        });
      }
    }
    return map;
  }, [data]);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Critical Path Analysis"
      description={`Analytical schedule bottlenecks and slack distribution for "${projectName}".`}
      maxWidth="2xl"
    >
      <div className="space-y-5">
        {/* Loading State */}
        {loading && (
          <div className="py-12 flex flex-col items-center justify-center space-y-2 text-center text-xs text-zinc-400 font-mono">
            <div className="h-5 w-5 border-2 border-zinc-600 border-t-amber-400 rounded-full animate-spin" />
            <span>Calculating topological critical paths and forward/backward passes...</span>
          </div>
        )}

        {errorMsg && (
          <div className="p-3 rounded-lg bg-rose-950/40 border border-rose-800/40 text-rose-300 text-xs">
            {errorMsg}
          </div>
        )}

        {!loading && data && (
          <div className="space-y-5">
            {/* Overview Metric Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 text-center">
                <div className="text-[10px] font-mono uppercase text-zinc-500">Project Completion</div>
                <div className="text-sm font-semibold text-emerald-400 font-mono mt-1">
                  {formatCalendarDate(data.projectCompletionDate)}
                </div>
              </div>

              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 text-center">
                <div className="text-[10px] font-mono uppercase text-zinc-500">Critical Tasks</div>
                <div className="text-sm font-semibold text-amber-400 font-mono mt-1">
                  {data.criticalTaskIds.length} tasks (0 slack)
                </div>
              </div>

              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 text-center">
                <div className="text-[10px] font-mono uppercase text-zinc-500">Critical Paths</div>
                <div className="text-sm font-semibold text-zinc-200 font-mono mt-1">
                  {data.criticalPaths.length} parallel path{data.criticalPaths.length !== 1 ? 's' : ''}
                </div>
              </div>
            </div>

            {/* Critical Paths Visualization (Section 31 & 32: Multiple paths displayed) */}
            <div className="space-y-2">
              <div className="text-xs font-mono uppercase text-zinc-400 flex items-center justify-between">
                <span>Critical Dependency Chains</span>
                <span className="text-[10px] text-amber-400 font-mono">Zero Float Sequences</span>
              </div>

              {data.criticalPaths.length === 0 ? (
                <div className="p-4 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-500 text-center font-mono">
                  No critical path chains identified (no dependencies in project).
                </div>
              ) : (
                <div className="space-y-2.5 max-h-52 overflow-y-auto pr-1">
                  {data.criticalPaths.map((path, pathIdx) => (
                    <div
                      key={pathIdx}
                      className="p-3 rounded-lg bg-zinc-950 border border-amber-500/30 space-y-2"
                    >
                      <div className="text-[10px] font-mono uppercase text-amber-400 font-medium">
                        Path {pathIdx + 1} ({path.length} tasks)
                      </div>
                      <div className="flex flex-wrap items-center gap-1.5 text-xs">
                        {path.map((taskId, nodeIdx) => {
                          const taskInfo = taskMap.get(taskId);
                          const taskTitle = taskInfo ? taskInfo.title : taskId;
                          return (
                            <React.Fragment key={taskId}>
                              <span className="px-2.5 py-1 rounded bg-zinc-900 border border-amber-500/40 text-zinc-100 font-medium">
                                {taskTitle}
                              </span>
                              {nodeIdx < path.length - 1 && (
                                <span className="text-amber-400 font-bold px-0.5">→</span>
                              )}
                            </React.Fragment>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* All Tasks Slack Distribution */}
            <div className="space-y-2">
              <div className="text-xs font-mono uppercase text-zinc-400 flex items-center justify-between">
                <span>Task Slack & Float Distribution</span>
                <span className="text-[10px] text-zinc-500 font-mono">{data.tasks.length} tasks</span>
              </div>

              <div className="max-h-48 overflow-y-auto space-y-1.5 pr-1">
                {data.tasks.map((task) => (
                  <div
                    key={task.taskId}
                    className="p-2.5 rounded-lg bg-zinc-950/70 border border-zinc-800/80 flex items-center justify-between text-xs"
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span
                        className={`h-2 w-2 rounded-full shrink-0 ${
                          task.isCritical ? 'bg-amber-400' : 'bg-emerald-400'
                        }`}
                      />
                      <span className="font-medium text-zinc-200 truncate">{task.title}</span>
                    </div>

                    <div className="flex items-center gap-2 font-mono text-[11px] shrink-0">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] ${
                          task.isCritical
                            ? 'bg-amber-950/60 text-amber-400 border border-amber-500/30'
                            : 'bg-zinc-800 text-zinc-400'
                        }`}
                      >
                        Slack: {task.totalSlackDays} days
                      </span>
                      {task.isCritical && (
                        <span className="text-amber-400 font-semibold text-[10px]">CRITICAL</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        <div className="pt-2 border-t border-zinc-800/80 flex justify-end">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
};
