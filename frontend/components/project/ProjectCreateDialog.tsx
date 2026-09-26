'use client';

import React, { useState } from 'react';
import { CreateProjectRequest, Project } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';

interface ProjectCreateDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (request: CreateProjectRequest) => Promise<Project>;
}

export const ProjectCreateDialog: React.FC<ProjectCreateDialogProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    if (!name.trim()) {
      setErrorMsg('Project name is required');
      return;
    }

    try {
      setSubmitting(true);
      await onSubmit({
        name: name.trim(),
        description: description.trim() || undefined,
      });
      setName('');
      setDescription('');
      onClose();
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to create project');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Create New Project"
      description="Create a workflow workspace for tasks, dependency graphs, and scheduling."
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {errorMsg && (
          <div className="p-2.5 rounded-lg bg-rose-950/40 border border-rose-800/40 text-rose-300 text-xs">
            {errorMsg}
          </div>
        )}

        <div className="space-y-1">
          <label htmlFor="project-name" className="block text-xs font-medium text-zinc-300">
            Project Name <span className="text-rose-400">*</span>
          </label>
          <input
            id="project-name"
            type="text"
            required
            placeholder="e.g. Core Platform Launch"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-zinc-400"
          />
        </div>

        <div className="space-y-1">
          <label htmlFor="project-description" className="block text-xs font-medium text-zinc-300">
            Description
          </label>
          <textarea
            id="project-description"
            rows={3}
            placeholder="Optional project scope or milestones..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full px-3 py-2 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-zinc-400"
          />
        </div>

        <div className="pt-3 border-t border-zinc-800/80 flex items-center justify-end gap-2">
          <Button variant="ghost" size="sm" type="button" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="primary" size="sm" type="submit" disabled={submitting}>
            {submitting ? 'Creating...' : 'Create Project'}
          </Button>
        </div>
      </form>
    </Modal>
  );
};
