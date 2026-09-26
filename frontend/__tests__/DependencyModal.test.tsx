import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DependencyModal } from '@/components/dependency/DependencyModal';
import { Task, TaskDependency } from '@/types';

describe('DependencyModal', () => {
  const currentTask: Task = {
    id: 'task-b',
    projectId: 'p-1',
    title: 'Implement API',
    workflowStatus: 'IN_PROGRESS',
    dependencyStatus: 'BLOCKED',
    durationDays: 3,
    version: 1,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  };

  const allTasks: Task[] = [
    {
      id: 'task-a',
      projectId: 'p-1',
      title: 'Design Database Schema',
      workflowStatus: 'BACKLOG',
      dependencyStatus: 'READY',
      durationDays: 2,
      version: 1,
      createdAt: '2026-06-01T00:00:00Z',
      updatedAt: '2026-06-01T00:00:00Z',
    },
    currentTask,
    {
      id: 'task-c',
      projectId: 'p-1',
      title: 'Build Frontend UI',
      workflowStatus: 'BACKLOG',
      dependencyStatus: 'BLOCKED',
      durationDays: 4,
      version: 1,
      createdAt: '2026-06-01T00:00:00Z',
      updatedAt: '2026-06-01T00:00:00Z',
    },
  ];

  const dependencies: TaskDependency[] = [
    // task-a -> task-b (task-a is prerequisite of task-b)
    {
      id: 'dep-1',
      predecessorTaskId: 'task-a',
      successorTaskId: 'task-b',
      createdAt: '2026-06-01T00:00:00Z',
    },
    // task-b -> task-c (task-c is dependent on task-b)
    {
      id: 'dep-2',
      predecessorTaskId: 'task-b',
      successorTaskId: 'task-c',
      createdAt: '2026-06-01T00:00:00Z',
    },
  ];

  it('renders prerequisites and dependents correctly', () => {
    render(
      <DependencyModal
        isOpen={true}
        task={currentTask}
        allTasks={allTasks}
        dependencies={dependencies}
        onClose={vi.fn()}
        onAddDependency={vi.fn()}
        onRemoveDependency={vi.fn()}
      />
    );

    // Prerequisite: Design Database Schema
    expect(screen.getByText('Design Database Schema')).toBeInTheDocument();
    expect(screen.getByText('Prerequisites (1)')).toBeInTheDocument();

    // Dependent: Build Frontend UI
    expect(screen.getByText('Build Frontend UI')).toBeInTheDocument();
    expect(screen.getByText('Dependents (1)')).toBeInTheDocument();
  });

  it('displays cycle error message clearly when backend rejects dependency', async () => {
    const onAddDependency = vi.fn().mockResolvedValue({
      success: false,
      error: 'Dependency not added. This dependency would create a cycle in the workflow.',
    });

    render(
      <DependencyModal
        isOpen={true}
        task={currentTask}
        allTasks={allTasks}
        dependencies={dependencies}
        onClose={vi.fn()}
        onAddDependency={onAddDependency}
        onRemoveDependency={vi.fn()}
      />
    );

    // Select task-c to add as prerequisite (which would create cycle b -> c -> b)
    const select = screen.getByLabelText(/Add Prerequisite Task/i);
    fireEvent.change(select, { target: { value: 'task-c' } });

    const addBtn = screen.getByRole('button', { name: /Add Dependency/i });
    fireEvent.click(addBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/This dependency would create a cycle in the workflow/i)
      ).toBeInTheDocument();
    });
  });

  it('invokes removeDependency when Remove button is clicked', async () => {
    const onRemove = vi.fn().mockResolvedValue(true);
    render(
      <DependencyModal
        isOpen={true}
        task={currentTask}
        allTasks={allTasks}
        dependencies={dependencies}
        onClose={vi.fn()}
        onAddDependency={vi.fn()}
        onRemoveDependency={onRemove}
      />
    );

    const removeBtn = screen.getByRole('button', { name: /Remove/i });
    fireEvent.click(removeBtn);

    await waitFor(() => {
      expect(onRemove).toHaveBeenCalledWith('task-a', 'task-b');
    });
  });
});
