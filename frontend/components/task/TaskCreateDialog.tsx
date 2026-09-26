'use client';

import React, { useState } from 'react';
import { CreateTaskRequest } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { getTodayCalendarDate, isValidCalendarDate } from '@/lib/utils/dates';

interface TaskCreateDialogProps {
  isOpen: boolean;
  projectId: string;
  onClose: () => void;
  onSubmit: (request: CreateTaskRequest) => Promise<void>;
}

export const TaskCreateDialog: React.FC<TaskCreateDialogProps> = ({
  isOpen,
  projectId,
  onClose,
  onSubmit,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [plannedStartDate, setPlannedStartDate] = useState(getTodayCalendarDate());
  const [durationDays, setDurationDays] = useState(1);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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
      setValidationError('Planned start date must be a valid date (YYYY-MM-DD)');
      return;
    }

    try {
      setSubmitting(true);
      await onSubmit({
        projectId,
        title: title.trim(),
        description: description.trim() || undefined,
        plannedStartDate: plannedStartDate || undefined,
        startDate: plannedStartDate || undefined,
        durationDays: Number(durationDays),
        workflowStatus: 'BACKLOG',
      });
      // Reset form and close
      setTitle('');
      setDescription('');
      setPlannedStartDate(getTodayCalendarDate());
      setDurationDays(1);
      onClose();
    } catch {
      // Error handled by parent or actionError
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Create New Task"
      description="Add a task to the project workflow. Dependencies and schedule propagate deterministically."
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {validationError && (
          <div className="p-2.5 rounded-lg bg-rose-950/40 border border-rose-800/40 text-rose-300 text-xs">
            {validationError}
          </div>
        )}

        <div className="space-y-1">
          <label htmlFor="task-title" className="block text-xs font-medium text-zinc-300">
            Task Title <span className="text-rose-400">*</span>
          </label>
          <input
            id="task-title"
            type="text"
            required
            placeholder="e.g. Design database schema"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-zinc-400"
          />
        </div>

        <div className="space-y-1">
          <label htmlFor="task-description" className="block text-xs font-medium text-zinc-300">
            Description
          </label>
          <textarea
            id="task-description"
            rows={3}
            placeholder="Optional context and objectives for this task..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-zinc-400"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <label htmlFor="task-planned-date" className="block text-xs font-medium text-zinc-300">
              Planned Start Date
            </label>
            <input
              id="task-planned-date"
              type="date"
              value={plannedStartDate}
              onChange={(e) => setPlannedStartDate(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-400"
            />
          </div>

          <div className="space-y-1">
            <label htmlFor="task-duration" className="block text-xs font-medium text-zinc-300">
              Duration (Days) <span className="text-rose-400">*</span>
            </label>
            <input
              id="task-duration"
              type="number"
              min={1}
              required
              value={durationDays}
              onChange={(e) => setDurationDays(parseInt(e.target.value, 10) || 1)}
              className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-400"
            />
          </div>
        </div>

        <div className="pt-3 border-t border-zinc-800/80 flex items-center justify-end gap-2">
          <Button variant="ghost" size="sm" type="button" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="primary" size="sm" type="submit" disabled={submitting}>
            {submitting ? 'Creating...' : 'Create Task'}
          </Button>
        </div>
      </form>
    </Modal>
  );
};
