import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { KanbanBoard } from '@/components/board/KanbanBoard';
import { Task } from '@/types';

describe('KanbanBoard', () => {
  const mockTasks: Task[] = [
    {
      id: 'task-1',
      projectId: 'proj-1',
      title: 'Backlog Item',
      workflowStatus: 'BACKLOG',
      dependencyStatus: 'READY',
      durationDays: 2,
      version: 0,
      createdAt: '2026-06-01T00:00:00Z',
      updatedAt: '2026-06-01T00:00:00Z',
    },
    {
      id: 'task-2',
      projectId: 'proj-1',
      title: 'Active Dev',
      workflowStatus: 'IN_PROGRESS',
      dependencyStatus: 'READY',
      durationDays: 3,
      version: 0,
      createdAt: '2026-06-01T00:00:00Z',
      updatedAt: '2026-06-01T00:00:00Z',
    },
    {
      id: 'task-3',
      projectId: 'proj-1',
      title: 'PR Review',
      workflowStatus: 'REVIEW',
      dependencyStatus: 'READY',
      durationDays: 1,
      version: 0,
      createdAt: '2026-06-01T00:00:00Z',
      updatedAt: '2026-06-01T00:00:00Z',
    },
    {
      id: 'task-4',
      projectId: 'proj-1',
      title: 'Finished Milestone',
      workflowStatus: 'DONE',
      dependencyStatus: 'READY',
      durationDays: 2,
      version: 0,
      createdAt: '2026-06-01T00:00:00Z',
      updatedAt: '2026-06-01T00:00:00Z',
    },
  ];

  it('renders all four workflow columns: Backlog, In Progress, Review, and Done', () => {
    render(
      <KanbanBoard
        tasks={mockTasks}
        prerequisitesByTask={new Map()}
        dependentsByTask={new Map()}
        criticalTaskIds={new Set()}
        onMoveTask={vi.fn()}
        onEditTask={vi.fn()}
        onOpenDependencies={vi.fn()}
        onOpenAiSuggestions={vi.fn()}
        onDeleteTask={vi.fn()}
        onCreateTaskClick={vi.fn()}
      />
    );

    expect(screen.getByText('Backlog')).toBeInTheDocument();
    expect(screen.getByText('In Progress')).toBeInTheDocument();
    expect(screen.getByText('Review')).toBeInTheDocument();
    expect(screen.getByText('Done')).toBeInTheDocument();

    // Verify task placement
    expect(screen.getByText('Backlog Item')).toBeInTheDocument();
    expect(screen.getByText('Active Dev')).toBeInTheDocument();
    expect(screen.getByText('PR Review')).toBeInTheDocument();
    expect(screen.getByText('Finished Milestone')).toBeInTheDocument();
  });

  it('renders empty project state when there are no tasks', () => {
    render(
      <KanbanBoard
        tasks={[]}
        prerequisitesByTask={new Map()}
        dependentsByTask={new Map()}
        criticalTaskIds={new Set()}
        onMoveTask={vi.fn()}
        onEditTask={vi.fn()}
        onOpenDependencies={vi.fn()}
        onOpenAiSuggestions={vi.fn()}
        onDeleteTask={vi.fn()}
        onCreateTaskClick={vi.fn()}
      />
    );

    expect(screen.getByText('No tasks yet')).toBeInTheDocument();
    expect(
      screen.getByText(/Create your first task to start building the workflow/i)
    ).toBeInTheDocument();
  });

  it('renders column empty states when a specific column has no tasks', () => {
    const singleTask: Task[] = [
      {
        id: 't-1',
        projectId: 'p-1',
        title: 'Only Backlog Task',
        workflowStatus: 'BACKLOG',
        dependencyStatus: 'READY',
        durationDays: 1,
        version: 0,
        createdAt: '2026-06-01T00:00:00Z',
        updatedAt: '2026-06-01T00:00:00Z',
      },
    ];

    render(
      <KanbanBoard
        tasks={singleTask}
        prerequisitesByTask={new Map()}
        dependentsByTask={new Map()}
        criticalTaskIds={new Set()}
        onMoveTask={vi.fn()}
        onEditTask={vi.fn()}
        onOpenDependencies={vi.fn()}
        onOpenAiSuggestions={vi.fn()}
        onDeleteTask={vi.fn()}
        onCreateTaskClick={vi.fn()}
      />
    );

    // Done, In Progress, Review should have "No tasks here."
    const emptyIndicators = screen.getAllByText('No tasks here.');
    expect(emptyIndicators.length).toBe(3);
  });
});
