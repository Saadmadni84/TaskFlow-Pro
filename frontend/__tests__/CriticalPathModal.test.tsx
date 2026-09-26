import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { CriticalPathModal } from '@/components/criticalpath/CriticalPathModal';
import { CriticalPathResponse } from '@/types';

describe('CriticalPathModal', () => {
  const mockCriticalPathResponse: CriticalPathResponse = {
    projectId: 'p-1',
    projectStartDate: '2026-06-01',
    projectCompletionDate: '2026-06-30',
    criticalTaskIds: ['t-1', 't-2', 't-3', 't-4'],
    criticalPaths: [
      ['t-1', 't-2', 't-4'], // Path 1: A -> B -> D
      ['t-1', 't-3', 't-4'], // Path 2: A -> C -> D
    ],
    tasks: [
      {
        taskId: 't-1',
        title: 'Design Database',
        durationDays: 5,
        earliestStart: '2026-06-01',
        earliestFinish: '2026-06-06',
        latestStart: '2026-06-01',
        latestFinish: '2026-06-06',
        totalSlackDays: 0,
        isCritical: true,
      },
      {
        taskId: 't-2',
        title: 'Implement API',
        durationDays: 10,
        earliestStart: '2026-06-06',
        earliestFinish: '2026-06-16',
        latestStart: '2026-06-06',
        latestFinish: '2026-06-16',
        totalSlackDays: 0,
        isCritical: true,
      },
      {
        taskId: 't-3',
        title: 'Build UI Component',
        durationDays: 10,
        earliestStart: '2026-06-06',
        earliestFinish: '2026-06-16',
        latestStart: '2026-06-06',
        latestFinish: '2026-06-16',
        totalSlackDays: 0,
        isCritical: true,
      },
      {
        taskId: 't-4',
        title: 'Deploy to Staging',
        durationDays: 4,
        earliestStart: '2026-06-16',
        earliestFinish: '2026-06-20',
        latestStart: '2026-06-16',
        latestFinish: '2026-06-20',
        totalSlackDays: 0,
        isCritical: true,
      },
      {
        taskId: 't-5',
        title: 'Write Documentation',
        durationDays: 2,
        earliestStart: '2026-06-06',
        earliestFinish: '2026-06-08',
        latestStart: '2026-06-18',
        latestFinish: '2026-06-20',
        totalSlackDays: 12,
        isCritical: false,
      },
    ],
  };

  it('renders project completion, critical tasks, slack, and multiple critical paths', async () => {
    const fetchCriticalPath = vi.fn().mockResolvedValue(mockCriticalPathResponse);

    render(
      <CriticalPathModal
        isOpen={true}
        projectName="Core Platform"
        onClose={vi.fn()}
        fetchCriticalPath={fetchCriticalPath}
      />
    );

    await waitFor(() => {
      // Project completion date
      expect(screen.getByText(/Jun 30, 2026/i)).toBeInTheDocument();
      // Metrics overview
      expect(screen.getByText(/4 tasks \(0 slack\)/i)).toBeInTheDocument();
      expect(screen.getByText(/2 parallel paths/i)).toBeInTheDocument();

      // Section 32: Multiple critical paths rendered
      expect(screen.getByText(/Path 1 \(3 tasks\)/i)).toBeInTheDocument();
      expect(screen.getByText(/Path 2 \(3 tasks\)/i)).toBeInTheDocument();

      // Slack distribution
      expect(screen.getByText(/Write Documentation/i)).toBeInTheDocument();
      expect(screen.getByText(/Slack: 12 days/i)).toBeInTheDocument();
    });
  });
});
