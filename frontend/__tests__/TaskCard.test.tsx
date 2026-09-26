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

  it('disables Move to Done for BLOCKED task and allows for READY task in menu', () => {
    const onMoveToStatus = vi.fn();
    const { rerender } = render(
      <TaskCard
        task={mockTask}
        prerequisiteCount={2}
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

    // Verify move to done is disabled when BLOCKED
    const blockedDoneBtn = screen.getByRole('button', { name: /→ Done/i });
    expect(blockedDoneBtn).toBeDisabled();
    fireEvent.click(blockedDoneBtn);
    expect(onMoveToStatus).not.toHaveBeenCalled();
  });

  it('allows Move to Done for READY task in menu', () => {
    const onMoveToStatus = vi.fn();
    render(
      <TaskCard
        task={{ ...mockTask, dependencyStatus: 'READY' }}
        prerequisiteCount={0}
        dependentCount={0}
        onEdit={vi.fn()}
        onOpenDependencies={vi.fn()}
        onOpenAiSuggestions={vi.fn()}
        onDelete={vi.fn()}
        onMoveToStatus={onMoveToStatus}
      />
    );

    fireEvent.click(screen.getByLabelText(/Actions for Implement Payment Gateway/));
    const activeDoneBtn = screen.getByRole('button', { name: /→ Done/i });
    expect(activeDoneBtn).not.toBeDisabled();
    fireEvent.click(activeDoneBtn);
    expect(onMoveToStatus).toHaveBeenCalledWith('t-1', 'DONE');
  });
});
