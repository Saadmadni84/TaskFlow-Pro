import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import SchedulingPage from '@/app/scheduling/page';
import ImpactPreviewPage from '@/app/impact-preview/page';
import { projectApi, taskApi, dependencyApi, schedulingApi } from '@/lib/api';
import { Project, Task, TaskDependency, ScheduleImpactPreviewResponse } from '@/types';

vi.mock('@/lib/api', () => ({
  projectApi: {
    getProjects: vi.fn(),
  },
  taskApi: {
    getTasksByProject: vi.fn(),
    updateTask: vi.fn(),
  },
  dependencyApi: {
    getDependenciesByProject: vi.fn(),
  },
  schedulingApi: {
    previewScheduleImpact: vi.fn(),
  },
  ApiClientError: class extends Error {
    status?: number;
    code?: string;
    constructor(message: string, status?: number, code?: string) {
      super(message);
      this.status = status;
      this.code = code;
    }
  },
}));

const mockProjects: Project[] = [
  {
    id: 'proj-1',
    name: 'TaskFlow Pro Core Platform',
    createdAt: '2026-06-01T00:00:00Z',
  },
];

const mockTasks: Task[] = [
  {
    id: 'task-1',
    projectId: 'proj-1',
    title: 'Requirements Analysis',
    workflowStatus: 'DONE',
    dependencyStatus: 'READY',
    plannedStartDate: '2026-06-01',
    scheduledStartDate: '2026-06-01',
    scheduledDueDate: '2026-06-03',
    durationDays: 3,
    version: 0,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
  {
    id: 'task-2',
    projectId: 'proj-1',
    title: 'Database Architecture',
    workflowStatus: 'IN_PROGRESS',
    dependencyStatus: 'READY',
    plannedStartDate: '2026-06-04',
    scheduledStartDate: '2026-06-04',
    scheduledDueDate: '2026-06-06',
    durationDays: 3,
    version: 0,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
];

const mockDependencies: TaskDependency[] = [
  {
    id: 'dep-1',
    predecessorTaskId: 'task-1',
    successorTaskId: 'task-2',
    createdAt: '2026-06-01T00:00:00Z',
  },
];

const mockPreviewResponse: ScheduleImpactPreviewResponse = {
  sourceTaskId: 'task-1',
  summary: {
    affectedTaskCount: 2,
    changedTaskCount: 2,
    unchangedTaskCount: 0,
    maximumDelayDays: 3,
  },
  tasks: [
    {
      taskId: 'task-1',
      title: 'Requirements Analysis',
      currentScheduledStart: '2026-06-01',
      proposedScheduledStart: '2026-06-04',
      currentScheduledDue: '2026-06-03',
      proposedScheduledDue: '2026-06-06',
      durationDays: 3,
      startShiftDays: 3,
      dueShiftDays: 3,
      shiftDays: 3,
      impactType: 'DELAYED',
      reasonType: 'DIRECT_CHANGE',
      constraintSourceTaskIds: [],
      reason: 'Source task planned start moved later by 3 days.',
    },
  ],
};

describe('Phase 5 SchedulingPage (Live Backend Integration)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (projectApi.getProjects as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockProjects);
    (taskApi.getTasksByProject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockTasks);
    (dependencyApi.getDependenciesByProject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockDependencies);
  });

  it('renders live projects, tasks, and executes propagate schedule mutation', async () => {
    (taskApi.updateTask as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ...mockTasks[0],
      plannedStartDate: '2026-06-04',
    });

    render(<SchedulingPage />);

    await waitFor(() => {
      expect(projectApi.getProjects).toHaveBeenCalled();
      expect(taskApi.getTasksByProject).toHaveBeenCalledWith('proj-1');
    });

    await waitFor(() => {
      expect(screen.getByText('Requirements Analysis')).toBeInTheDocument();
      expect(screen.getByText('Database Architecture')).toBeInTheDocument();
    });

    const propagateButton = screen.getByRole('button', { name: /Propagate Schedule Update/i });
    expect(propagateButton).not.toBeDisabled();

    fireEvent.click(propagateButton);

    await waitFor(() => {
      expect(taskApi.updateTask).toHaveBeenCalledWith('task-1', expect.objectContaining({
        plannedStartDate: '2026-06-01',
      }));
    });
  });
});

describe('Phase 6 ImpactPreviewPage (Live Backend Integration)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (projectApi.getProjects as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockProjects);
    (taskApi.getTasksByProject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockTasks);
    (schedulingApi.previewScheduleImpact as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockPreviewResponse);
  });

  it('renders project tasks, triggers preview calculation, and renders downstream impact table', async () => {
    render(<ImpactPreviewPage />);

    await waitFor(() => {
      expect(projectApi.getProjects).toHaveBeenCalled();
      expect(taskApi.getTasksByProject).toHaveBeenCalledWith('proj-1');
    });

    const runBtn = await screen.findByRole('button', { name: /Recalculate Impact/i });
    fireEvent.click(runBtn);

    await waitFor(() => {
      expect(schedulingApi.previewScheduleImpact).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(screen.getByText('Authoritative Backend Impact Simulation')).toBeInTheDocument();
      expect(screen.getByText('Source task planned start moved later by 3 days.')).toBeInTheDocument();
    });
  });
});
