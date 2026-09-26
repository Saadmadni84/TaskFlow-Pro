'use client';

import React, { useState, useEffect } from 'react';
import { Task, TaskStatus, UpdateTaskRequest, ScheduleImpactPreviewResponse } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { ScheduleImpactPreviewModal } from '@/components/scheduling/ScheduleImpactPreviewModal';
import { isValidCalendarDate, formatDateRange } from '@/lib/utils/dates';

interface TaskEditDialogProps {
  isOpen: boolean;
  task: Task | null;
  onClose: () => void;
  onUpdate: (taskId: string, payload: UpdateTaskRequest) => Promise<Task | null>;
  onPreviewImpact: (taskId: string, plannedStartDate: string) => Promise<ScheduleImpactPreviewResponse>;
}

export const TaskEditDialog: React.FC<TaskEditDialogProps> = ({
  isOpen,
  task,
  onClose,
  onUpdate,
  onPreviewImpact,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [plannedStartDate, setPlannedStartDate] = useState('');
  const [durationDays, setDurationDays] = useState(1);
  const [workflowStatus, setWorkflowStatus] = useState<TaskStatus>('BACKLOG');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Impact Preview State
  const [showPreviewModal, setShowPreviewModal] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewData, setPreviewData] = useState<ScheduleImpactPreviewResponse | null>(null);

  useEffect(() => {
    if (task) {
      setTitle(task.title || '');
      setDescription(task.description || '');
      setPlannedStartDate(task.plannedStartDate || task.startDate || '');
      setDurationDays(task.durationDays || 1);
      setWorkflowStatus(task.workflowStatus);
      setValidationError(null);
    }
  }, [task]);

  if (!task) return null;

  const originalDate = task.plannedStartDate || task.startDate;
  const isDateChanged = plannedStartDate !== originalDate;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);

    if (!title.trim()) {
      setValidationError('Task title is required');
      return;
    }

    if (durationDays < 1) {
      setValidationError('Duration must be at least 1 day');
      return;
    }

    if (plannedStartDate && !isValidCalendarDate(plannedStartDate)) {
      setValidationError('Planned start date must be valid (YYYY-MM-DD)');
      return;
    }

    // Section 17 & 53: If planned start date has changed, intercept and display impact preview first!
    if (isDateChanged && plannedStartDate) {
      try {
        setPreviewLoading(true);
        setShowPreviewModal(true);
        const preview = await onPreviewImpact(task.id, plannedStartDate);
        setPreviewData(preview);
      } catch (err: unknown) {
        setShowPreviewModal(false);
        const errMsg = err instanceof Error ? err.message : 'Failed to calculate schedule impact preview';
        setValidationError(errMsg);
      } finally {
        setPreviewLoading(false);
      }
      return;
    }

    // Direct update if date didn't change
    await executeUpdate();
  };

  const executeUpdate = async () => {
    try {
      setSubmitting(true);
      const payload: UpdateTaskRequest = {
        title: title.trim(),
        description: description.trim() || undefined,
        plannedStartDate: plannedStartDate || undefined,
        startDate: plannedStartDate || undefined,
        durationDays: Number(durationDays),
        workflowStatus,
      };

      const result = await onUpdate(task.id, payload);
      if (result) {
        setShowPreviewModal(false);
        onClose();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Modal
        isOpen={isOpen && !showPreviewModal}
        onClose={onClose}
        title="Edit Task Details"
        description="Update task fields. Scheduled dates and readiness are server-authoritative."
        maxWidth="md"
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          {validationError && (
            <div className="p-2.5 rounded-lg bg-rose-950/40 border border-rose-800/40 text-rose-300 text-xs">
              {validationError}
            </div>
          )}

          {/* Current Read-Only Schedule Context */}
          <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 space-y-1.5 text-xs">
            <div className="flex items-center justify-between text-zinc-400">
              <span className="font-mono text-[10px] uppercase text-zinc-500">Current Readiness:</span>
              <span
                className={`font-mono text-[11px] font-semibold ${
                  task.dependencyStatus === 'BLOCKED' ? 'text-rose-400' : 'text-emerald-400'
                }`}
              >
                {task.dependencyStatus}
              </span>
            </div>
            <div className="flex items-center justify-between text-zinc-400">
              <span className="font-mono text-[10px] uppercase text-zinc-500">Scheduled Dates:</span>
              <span className="font-mono text-[11px] text-zinc-200">
                {formatDateRange(
                  task.scheduledStartDate || task.startDate,
                  task.scheduledDueDate || task.dueDate
                )}
              </span>
            </div>
          </div>

          <div className="space-y-1">
            <label htmlFor="edit-task-title" className="block text-xs font-medium text-zinc-300">
              Task Title <span className="text-rose-400">*</span>
            </label>
            <input
              id="edit-task-title"
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-zinc-400"
            />
          </div>

          <div className="space-y-1">
            <label htmlFor="edit-task-description" className="block text-xs font-medium text-zinc-300">
              Description
            </label>
            <textarea
              id="edit-task-description"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-zinc-400"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label htmlFor="edit-task-planned-date" className="block text-xs font-medium text-zinc-300">
                Planned Start Date
              </label>
              <input
                id="edit-task-planned-date"
                type="date"
                value={plannedStartDate}
                onChange={(e) => setPlannedStartDate(e.target.value)}
                className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-400"
              />
              {isDateChanged && (
                <span className="text-[10px] text-amber-400 block mt-0.5">
                  Changing date will trigger Impact Preview
                </span>
              )}
            </div>

            <div className="space-y-1">
              <label htmlFor="edit-task-duration" className="block text-xs font-medium text-zinc-300">
                Duration (Days) <span className="text-rose-400">*</span>
              </label>
              <input
                id="edit-task-duration"
                type="number"
                min={1}
                required
                value={durationDays}
                onChange={(e) => setDurationDays(parseInt(e.target.value, 10) || 1)}
                className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-400"
              />
            </div>
          </div>

          <div className="space-y-1">
            <label htmlFor="edit-task-status" className="block text-xs font-medium text-zinc-300">
              Workflow Status
            </label>
            <select
              id="edit-task-status"
              value={workflowStatus}
              onChange={(e) => setWorkflowStatus(e.target.value as TaskStatus)}
              className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-400"
            >
              <option value="BACKLOG">Backlog</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="REVIEW">Review</option>
              <option value="DONE">Done</option>
            </select>
          </div>

          <div className="pt-3 border-t border-zinc-800/80 flex items-center justify-end gap-2">
            <Button variant="ghost" size="sm" type="button" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" type="submit" disabled={submitting}>
              {submitting
                ? 'Saving...'
                : isDateChanged
                ? 'Preview Impact & Save'
                : 'Save Changes'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Impact Preview Intercept Modal */}
      {showPreviewModal && (
        <ScheduleImpactPreviewModal
          isOpen={showPreviewModal}
          previewData={previewData}
          sourceTaskTitle={task.title}
          originalDate={originalDate}
          proposedDate={plannedStartDate}
          loading={previewLoading}
          applying={submitting}
          onCancel={() => setShowPreviewModal(false)}
          onApply={executeUpdate}
        />
      )}
    </>
  );
};
