import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { TaskCreateDialog } from '@/components/task/TaskCreateDialog';
import { TaskEditDialog } from '@/components/task/TaskEditDialog';
import { TaskDeleteDialog } from '@/components/task/TaskDeleteDialog';
import { Task } from '@/types';

describe('Task Dialogs', () => {
  it('TaskCreateDialog submits valid form payload', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <TaskCreateDialog
        isOpen={true}
        projectId="project-abc"
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />
    );

    fireEvent.change(screen.getByLabelText(/Task Title/i), {
      target: { value: 'New Test Task' },
    });
    fireEvent.change(screen.getByLabelText(/Description/i), {
      target: { value: 'Detailed description' },
    });
    fireEvent.change(screen.getByLabelText(/Duration/i), {
      target: { value: '5' },
    });

    fireEvent.click(screen.getByRole('button', { name: /Create Task/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          projectId: 'project-abc',
          title: 'New Test Task',
          description: 'Detailed description',
          durationDays: 5,
        })
      );
    });
  });

  it('TaskEditDialog submits updated fields', async () => {
    const mockTask: Task = {
      id: 'task-100',
      projectId: 'project-abc',
      title: 'Existing Task',
      description: 'Existing Description',
      workflowStatus: 'BACKLOG',
      dependencyStatus: 'READY',
      plannedStartDate: '2026-06-15',
      durationDays: 3,
      version: 2,
      createdAt: '2026-06-01T00:00:00Z',
      updatedAt: '2026-06-01T00:00:00Z',
    };

    const onUpdate = vi.fn().mockResolvedValue(mockTask);
    const onPreview = vi.fn();

    render(
      <TaskEditDialog
        isOpen={true}
        task={mockTask}
        onClose={vi.fn()}
        onUpdate={onUpdate}
        onPreviewImpact={onPreview}
      />
    );

    fireEvent.change(screen.getByLabelText(/Task Title/i), {
      target: { value: 'Updated Title' },
    });

    fireEvent.click(screen.getByRole('button', { name: /Save Changes/i }));

    await waitFor(() => {
      expect(onUpdate).toHaveBeenCalledWith(
        'task-100',
        expect.objectContaining({
          title: 'Updated Title',
        })
      );
    });
    // Planned date didn't change, so preview should not be called
    expect(onPreview).not.toHaveBeenCalled();
  });

  it('TaskDeleteDialog prompts with confirmation and calls deletion handler', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(
      <TaskDeleteDialog
        isOpen={true}
        taskId="task-del-1"
        taskTitle="Implement Payment API"
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />
    );

    expect(screen.getByText(/Are you sure you want to delete/i)).toBeInTheDocument();
    expect(screen.getByText(/Implement Payment API/i)).toBeInTheDocument();

    const deleteBtn = screen.getByRole('button', { name: /Delete Task/i });
    fireEvent.click(deleteBtn);

    expect(onConfirm).toHaveBeenCalledWith('task-del-1');
  });
});
