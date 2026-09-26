'use client';

import React from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';

interface TaskDeleteDialogProps {
  isOpen: boolean;
  taskId: string | null;
  taskTitle: string;
  onClose: () => void;
  onConfirm: (taskId: string) => Promise<void>;
  deleting?: boolean;
}

export const TaskDeleteDialog: React.FC<TaskDeleteDialogProps> = ({
  isOpen,
  taskId,
  taskTitle,
  onClose,
  onConfirm,
  deleting = false,
}) => {
  if (!isOpen || !taskId) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Delete Task"
      description="Deleting a task may affect dependent tasks in the workflow graph."
      maxWidth="sm"
    >
      <div className="space-y-4">
        <p className="text-xs text-zinc-300 leading-relaxed">
          Are you sure you want to delete <span className="font-semibold text-zinc-100">&ldquo;{taskTitle}&rdquo;</span>?
        </p>

        <div className="p-3 rounded-lg bg-rose-950/30 border border-rose-800/30 text-rose-300 text-xs">
          The server enforces dependency constraints. If other tasks depend on this task, deletion will be safely rejected.
        </div>

        <div className="pt-2 border-t border-zinc-800/80 flex items-center justify-end gap-2">
          <Button variant="ghost" size="sm" type="button" onClick={onClose} disabled={deleting}>
            Cancel
          </Button>
          <Button
            variant="danger"
            size="sm"
            type="button"
            disabled={deleting}
            onClick={() => onConfirm(taskId)}
          >
            {deleting ? 'Deleting...' : 'Delete Task'}
          </Button>
        </div>
      </div>
    </Modal>
  );
};
