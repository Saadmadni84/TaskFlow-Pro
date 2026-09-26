import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { TaskCard } from '@/components/board/TaskCard';
import { Task } from '@/types';

describe('TaskCard', () => {
  const mockTask: Task = {
    id: 't-1',
    projectId: 'p-1',
    title: 'Implement Payment Gateway',
    description: 'Stripe integration with idempotency keys',
    workflowStatus: 'IN_PROGRESS',
    dependencyStatus: 'BLOCKED',
    plannedStartDate: '2026-06-10',
    scheduledStartDate: '2026-06-12',
    scheduledDueDate: '2026-06-16',
    durationDays: 4,
    version: 1,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  };

  it('renders task title, BLOCKED dependency state, and dates correctly', () => {
    render(
      <TaskCard
        task={mockTask}
        prerequisiteCount={2}
        dependentCount={1}
        isCritical={false}
        onEdit={vi.fn()}
        onOpenDependencies={vi.fn()}
        onOpenAiSuggestions={vi.fn()}
        onDelete={vi.fn()}
        onMoveToStatus={vi.fn()}
      />
    );

    // Title and description
    expect(screen.getByText('Implement Payment Gateway')).toBeInTheDocument();
    expect(screen.getByText('Stripe integration with idempotency keys')).toBeInTheDocument();

    // Blocked status and explanation
    expect(screen.getByText(/Blocked/)).toBeInTheDocument();
    expect(screen.getByText(/Waiting for 2 prerequisites/)).toBeInTheDocument();

    // Dates
    expect(screen.getByText(/Planned:/)).toBeInTheDocument();
    expect(screen.getByText(/Jun 10, 2026/)).toBeInTheDocument();
    expect(screen.getByText(/Scheduled:/)).toBeInTheDocument();
    expect(screen.getByText(/Jun 12 → Jun 16, 2026/)).toBeInTheDocument();
  });

  it('renders READY dependency status when task is ready', () => {
    const readyTask: Task = {
      ...mockTask,
      dependencyStatus: 'READY',
    };

    render(
      <TaskCard
        task={readyTask}
        prerequisiteCount={0}
        dependentCount={2}
        isCritical={true}
        onEdit={vi.fn()}
        onOpenDependencies={vi.fn()}
        onOpenAiSuggestions={vi.fn()}
        onDelete={vi.fn()}
        onMoveToStatus={vi.fn()}
      />
    );

    expect(screen.getByText('Ready')).toBeInTheDocument();
    expect(screen.getByText('Critical')).toBeInTheDocument();
    expect(screen.queryByText(/Waiting for/)).not.toBeInTheDocument();
  });

  it('provides accessible Move to... options in the compact menu', () => {
    const onMoveToStatus = vi.fn();
    render(
      <TaskCard
        task={mockTask}
        prerequisiteCount={0}
        dependentCount={0}
        onEdit={vi.fn()}
        onOpenDependencies={vi.fn()}
        onOpenAiSuggestions={vi.fn()}
        onDelete={vi.fn()}
        onMoveToStatus={onMoveToStatus}
      />
    );

    // Click menu button
    const menuBtn = screen.getByLabelText(/Actions for Implement Payment Gateway/);
    fireEvent.click(menuBtn);

    // Verify accessible move actions exist
    const moveToDone = screen.getByText('→ Done');
    expect(moveToDone).toBeInTheDocument();

    fireEvent.click(moveToDone);
    expect(onMoveToStatus).toHaveBeenCalledWith('t-1', 'DONE');
  });
});
