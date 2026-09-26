'use client';

import React, { useState, useRef, useEffect } from 'react';
import { Task, TaskStatus } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { formatCalendarDate, formatDateRange } from '@/lib/utils/dates';

interface TaskCardProps {
  task: Task;
  prerequisiteCount: number;
  dependentCount: number;
  isCritical?: boolean;
  onEdit: (task: Task) => void;
  onOpenDependencies: (task: Task) => void;
  onOpenAiSuggestions: (task: Task) => void;
  onDelete: (taskId: string) => void;
  onMoveToStatus: (taskId: string, newStatus: TaskStatus) => void;
}

const ALL_STATUSES: { status: TaskStatus; label: string }[] = [
  { status: 'BACKLOG', label: 'Backlog' },
  { status: 'IN_PROGRESS', label: 'In Progress' },
  { status: 'REVIEW', label: 'Review' },
  { status: 'DONE', label: 'Done' },
];

export const TaskCard: React.FC<TaskCardProps> = ({
  task,
  prerequisiteCount,
  dependentCount,
  isCritical = false,
  onEdit,
  onOpenDependencies,
  onOpenAiSuggestions,
  onDelete,
  onMoveToStatus,
}) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close menu on click outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    if (menuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [menuOpen]);

  // Drag handlers
  const handleDragStart = (e: React.DragEvent) => {
    e.dataTransfer.setData('application/json', JSON.stringify({ taskId: task.id }));
    e.dataTransfer.effectAllowed = 'move';
    setIsDragging(true);
  };

  const handleDragEnd = () => {
    setIsDragging(false);
  };

  const isBlocked = task.dependencyStatus === 'BLOCKED';
  const isReady = task.dependencyStatus === 'READY';

  // Planned date vs Scheduled date range
  const plannedDateStr = task.plannedStartDate || task.startDate;
  const scheduledRangeStr = formatDateRange(
    task.scheduledStartDate || task.startDate,
    task.scheduledDueDate || task.dueDate
  );

  return (
    <div
      draggable
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      aria-label={`Task card: ${task.title}`}
      className={`group relative rounded-lg border bg-zinc-900/90 p-4 transition-all duration-150 select-none shadow-sm hover:shadow-md hover:border-zinc-700/80 ${
        isDragging
          ? 'opacity-40 border-dashed border-zinc-600 scale-[0.98]'
          : isCritical
          ? 'border-amber-500/40 hover:border-amber-500/60'
          : 'border-zinc-800/80'
      }`}
    >
      {/* Top Header: Critical indicator & Action Menu */}
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex flex-wrap items-center gap-1.5">
          {/* Dependency Status Indicator */}
          {isBlocked ? (
            <Badge variant="blocked" className="text-[10px] py-0 px-2 font-mono">
              Blocked {prerequisiteCount > 0 ? `(${prerequisiteCount} prereq${prerequisiteCount > 1 ? 's' : ''})` : ''}
            </Badge>
          ) : isReady ? (
            <Badge variant="ready" className="text-[10px] py-0 px-2 font-mono">
              Ready
            </Badge>
          ) : null}

          {/* Critical Path Indicator */}
          {isCritical && (
            <span
              title="On Critical Path (0 Slack)"
              className="inline-flex items-center gap-1 text-[10px] font-mono font-medium px-2 py-0.5 rounded-full bg-amber-950/60 text-amber-400 border border-amber-500/30"
            >
              <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
              Critical
            </span>
          )}
        </div>

        {/* Compact Action Menu (•••) */}
        <div className="relative shrink-0" ref={menuRef}>
          <button
            type="button"
            aria-label={`Actions for ${task.title}`}
            onClick={(e) => {
              e.stopPropagation();
              setMenuOpen(!menuOpen);
            }}
            className="p-1 rounded text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition-colors"
          >
            <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
              <path d="M6 10a2 2 0 11-4 0 2 2 0 014 0zM12 10a2 2 0 11-4 0 2 2 0 014 0zM16 12a2 2 0 100-4 2 2 0 000 4z" />
            </svg>
          </button>

          {/* Dropdown Menu */}
          {menuOpen && (
            <div className="absolute right-0 top-6 w-48 bg-zinc-950 border border-zinc-800 rounded-lg shadow-xl py-1 z-30 text-xs">
              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  onEdit(task);
                }}
                className="w-full text-left px-3 py-1.5 text-zinc-300 hover:bg-zinc-800 hover:text-zinc-100 flex items-center gap-2"
              >
                <span>Edit details</span>
              </button>

              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  onOpenDependencies(task);
                }}
                className="w-full text-left px-3 py-1.5 text-zinc-300 hover:bg-zinc-800 hover:text-zinc-100 flex items-center justify-between"
              >
                <span>View dependencies</span>
                <span className="text-[10px] text-zinc-500 font-mono">
                  {prerequisiteCount + dependentCount}
                </span>
              </button>

              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  onOpenAiSuggestions(task);
                }}
                className="w-full text-left px-3 py-1.5 text-amber-400 hover:bg-zinc-800 flex items-center gap-1.5"
              >
                <span>AI suggestions</span>
              </button>

              {/* Accessible Move to... Menu */}
              <div className="border-t border-zinc-800/80 my-1" />
              <div className="px-3 py-1 text-[10px] font-mono uppercase text-zinc-500">
                Move to...
              </div>
              {ALL_STATUSES.map(({ status, label }) => {
                if (status === task.workflowStatus) return null;
                const isBlockedDone = isBlocked && status === 'DONE';
                return (
                  <button
                    key={status}
                    type="button"
                    disabled={isBlockedDone}
                    onClick={() => {
                      if (isBlockedDone) return;
                      setMenuOpen(false);
                      onMoveToStatus(task.id, status);
                    }}
                    title={isBlockedDone ? 'Cannot move to Done: Prerequisites must be completed first' : undefined}
                    className={`w-full text-left px-3 py-1 pl-4 flex items-center justify-between text-xs transition-colors ${
                      isBlockedDone
                        ? 'text-zinc-600 cursor-not-allowed opacity-60'
                        : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100'
                    }`}
                  >
                    <span>→ {label}</span>
                    {isBlockedDone && (
                      <span className="text-[9px] font-mono text-rose-500 uppercase tracking-tight">
                        Blocked
                      </span>
                    )}
                  </button>
                );
              })}

              <div className="border-t border-zinc-800/80 my-1" />
              <button
                type="button"
                onClick={() => {
                  setMenuOpen(false);
                  onDelete(task.id);
                }}
                className="w-full text-left px-3 py-1.5 text-rose-400 hover:bg-rose-950/40 hover:text-rose-300 flex items-center gap-2"
              >
                <span>Delete task</span>
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Task Title */}
      <h3
        onClick={() => onEdit(task)}
        className="font-medium text-sm text-zinc-100 leading-snug cursor-pointer hover:text-zinc-300 transition-colors line-clamp-2 mb-2"
      >
        {task.title}
      </h3>

      {/* Description preview if present */}
      {task.description && (
        <p className="text-xs text-zinc-400 line-clamp-2 mb-3 leading-relaxed">
          {task.description}
        </p>
      )}

      {/* Dependency Blocked Explanation if BLOCKED */}
      {isBlocked && (
        <div className="mb-3 px-2.5 py-1.5 rounded bg-rose-950/30 border border-rose-800/30 text-[11px] text-rose-300/90 leading-tight">
          Waiting for {prerequisiteCount > 0 ? `${prerequisiteCount} prerequisite${prerequisiteCount > 1 ? 's' : ''}` : 'prerequisites to complete'}
        </div>
      )}

      {/* Dates Section: Clearly distinguish Planned vs Scheduled */}
      <div className="pt-2 border-t border-zinc-800/60 space-y-1 text-[11px]">
        {plannedDateStr && (
          <div className="flex items-center justify-between text-zinc-400">
            <span className="text-[10px] font-mono text-zinc-500 uppercase">Planned:</span>
            <span>{formatCalendarDate(plannedDateStr)}</span>
          </div>
        )}

        <div className="flex items-center justify-between text-zinc-300">
          <span className="text-[10px] font-mono text-zinc-500 uppercase">Scheduled:</span>
          <span className="font-mono text-[11px] text-zinc-200">{scheduledRangeStr}</span>
        </div>
      </div>

      {/* Footer: Prerequisites / Dependents pill */}
      {(prerequisiteCount > 0 || dependentCount > 0) && (
        <div className="mt-2.5 pt-2 border-t border-zinc-800/40 flex items-center justify-between text-[10px] text-zinc-400 font-mono">
          <span className="flex items-center gap-1">
            <svg className="w-3 h-3 text-zinc-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
            </svg>
            {prerequisiteCount} prereq{prerequisiteCount !== 1 ? 's' : ''}
          </span>
          <span>
            {dependentCount} dependent{dependentCount !== 1 ? 's' : ''}
          </span>
        </div>
      )}
    </div>
  );
};
