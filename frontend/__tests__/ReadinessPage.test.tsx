import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import ReadinessPage from '@/app/readiness/page';
import { projectApi, taskApi, dependencyApi } from '@/lib/api';
import { Project, Task, TaskDependency } from '@/types';

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
    name: 'Readiness Test Project',
    createdAt: '2026-06-01T00:00:00Z',
  },
];

const mockTasks: Task[] = [
  {
    id: 'task-a',
    projectId: 'proj-1',
    title: 'Task A (Database Schema)',
    workflowStatus: 'IN_PROGRESS',
    dependencyStatus: 'READY',
    durationDays: 3,
    version: 0,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
  {
    id: 'task-b',
    projectId: 'proj-1',
    title: 'Task B (Backend API)',
    workflowStatus: 'BACKLOG',
    dependencyStatus: 'BLOCKED',
    durationDays: 5,
    version: 0,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
];

const mockDependencies: TaskDependency[] = [
  {
    id: 'dep-ab',
    predecessorTaskId: 'task-a',
    successorTaskId: 'task-b',
    createdAt: '2026-06-01T00:00:00Z',
  },
];

describe('Phase 4 ReadinessPage (Live Backend Integration)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (projectApi.getProjects as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockProjects);
    (taskApi.getTasksByProject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockTasks);
    (dependencyApi.getDependenciesByProject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockDependencies);
  });

  it('loads real backend tasks and disables Mark Done for BLOCKED task with clear message', async () => {
    render(<ReadinessPage />);

    await waitFor(() => {
      expect(projectApi.getProjects).toHaveBeenCalled();
      expect(taskApi.getTasksByProject).toHaveBeenCalledWith('proj-1');
      expect(dependencyApi.getDependenciesByProject).toHaveBeenCalledWith('proj-1');
    });

    await waitFor(() => {
      expect(screen.getByText('Task A (Database Schema)')).toBeInTheDocument();
    });
    const taskAButton = screen.getByRole('button', { name: /Mark Done \(→ DONE\)/i });
    expect(taskAButton).not.toBeDisabled();

    // Check Task B is rendered and its Mark Done button is DISABLED
    expect(screen.getByText('Task B (Backend API)')).toBeInTheDocument();
    const taskBButton = screen.getByRole('button', { name: /Mark Done \(Blocked\)/i });
    expect(taskBButton).toBeDisabled();

    // Check clear indicator that prerequisites must be completed first
    expect(
      screen.getByText('Prerequisites must be completed first before marking this task as DONE.')
    ).toBeInTheDocument();

    // Clicking disabled button should not invoke backend updateTask
    fireEvent.click(taskBButton);
    expect(taskApi.updateTask).not.toHaveBeenCalled();
  });

  it('calls backend taskApi.updateTask when completing a READY task', async () => {
    (taskApi.updateTask as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ...mockTasks[0],
      workflowStatus: 'DONE',
    });

    render(<ReadinessPage />);

    await waitFor(() => {
      expect(screen.getByText('Task A (Database Schema)')).toBeInTheDocument();
    });

    const taskAButton = screen.getByRole('button', { name: /Mark Done \(→ DONE\)/i });
    fireEvent.click(taskAButton);

    await waitFor(() => {
      expect(taskApi.updateTask).toHaveBeenCalledWith('task-a', expect.objectContaining({
        workflowStatus: 'DONE',
      }));
    });
  });
});
