import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ScheduleImpactPreviewModal } from '@/components/scheduling/ScheduleImpactPreviewModal';
import { ScheduleImpactPreviewResponse } from '@/types';

describe('ScheduleImpactPreviewModal', () => {
  const mockPreviewData: ScheduleImpactPreviewResponse = {
    sourceTaskId: 'task-1',
    summary: {
      affectedTaskCount: 3,
      changedTaskCount: 2,
      unchangedTaskCount: 1,
      maximumDelayDays: 3,
    },
    tasks: [
      {
        taskId: 'task-1',
        title: 'Implement API',
        currentPlannedStart: '2026-06-10',
        proposedPlannedStart: '2026-06-13',
        currentScheduledStart: '2026-06-10',
        proposedScheduledStart: '2026-06-13',
        currentScheduledDue: '2026-06-14',
        proposedScheduledDue: '2026-06-17',
        durationDays: 4,
        startShiftDays: 3,
        dueShiftDays: 3,
        shiftDays: 3,
        impactType: 'DELAYED',
        reasonType: 'DIRECT_CHANGE',
        constraintSourceTaskIds: [],
        reason: 'Direct modification of planned start date (+3 days)',
      },
      {
        taskId: 'task-2',
        title: 'Build Frontend UI',
        currentPlannedStart: '2026-06-12',
        proposedPlannedStart: '2026-06-12',
        currentScheduledStart: '2026-06-14',
        proposedScheduledStart: '2026-06-17',
        currentScheduledDue: '2026-06-18',
        proposedScheduledDue: '2026-06-21',
        durationDays: 4,
        startShiftDays: 3,
        dueShiftDays: 3,
        shiftDays: 3,
        impactType: 'DELAYED',
        reasonType: 'BINDING_PREDECESSOR',
        constraintSourceTaskIds: ['task-1'],
        reason: 'Delayed by prerequisite "Implement API" (+3 days)',
      },
    ],
  };

  it('renders proposed date change and affected tasks with shift metrics', () => {
    render(
      <ScheduleImpactPreviewModal
        isOpen={true}
        previewData={mockPreviewData}
        sourceTaskTitle="Implement API"
        originalDate="2026-06-10"
        proposedDate="2026-06-13"
        onCancel={vi.fn()}
        onApply={vi.fn()}
      />
    );

    // Verify source change title and dates
    expect(screen.getByText(/Moving “Implement API”/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Jun 10, 2026/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/Jun 13, 2026/i).length).toBeGreaterThanOrEqual(1);

    // Summary counts
    expect(screen.getByText('3')).toBeInTheDocument(); // Affected tasks
    expect(screen.getByText('2')).toBeInTheDocument(); // Changed schedule
    expect(screen.getByText('+3d')).toBeInTheDocument(); // Max delay

    // Tasks list and shift
    expect(screen.getByText('Build Frontend UI')).toBeInTheDocument();
    const plusThrees = screen.getAllByText('+3 days');
    expect(plusThrees.length).toBeGreaterThanOrEqual(1);

    // Explanations
    expect(
      screen.getByText(/Delayed by prerequisite "Implement API" \(\+3 days\)/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Non-compounding schedule:/i)).toBeInTheDocument();
  });

  it('calls onApply when Apply Schedule Change is clicked', () => {
    const onApply = vi.fn();
    render(
      <ScheduleImpactPreviewModal
        isOpen={true}
        previewData={mockPreviewData}
        sourceTaskTitle="Implement API"
        originalDate="2026-06-10"
        proposedDate="2026-06-13"
        onCancel={vi.fn()}
        onApply={onApply}
      />
    );

    const applyBtn = screen.getByRole('button', { name: /Apply Schedule Change/i });
    fireEvent.click(applyBtn);

    expect(onApply).toHaveBeenCalled();
  });

  it('calls onCancel without applying mutation when Cancel is clicked', () => {
    const onCancel = vi.fn();
    const onApply = vi.fn();
    render(
      <ScheduleImpactPreviewModal
        isOpen={true}
        previewData={mockPreviewData}
        sourceTaskTitle="Implement API"
        originalDate="2026-06-10"
        proposedDate="2026-06-13"
        onCancel={onCancel}
        onApply={onApply}
      />
    );

    const cancelBtn = screen.getByRole('button', { name: /Cancel/i });
    fireEvent.click(cancelBtn);

    expect(onCancel).toHaveBeenCalled();
    expect(onApply).not.toHaveBeenCalled();
  });
});
