import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useProjectBoard } from '@/hooks/useProjectBoard';
import { taskApi, dependencyApi } from '@/lib/api';
import { Task } from '@/types';

vi.mock('@/lib/api', () => ({
  taskApi: {
    getTasksByProject: vi.fn(),
    updateTask: vi.fn(),
    createTask: vi.fn(),
    deleteTask: vi.fn(),
  },
  dependencyApi: {
    getDependenciesByProject: vi.fn(),
    createDependency: vi.fn(),
    deleteDependency: vi.fn(),
  },
  schedulingApi: {
    previewScheduleImpact: vi.fn(),
  },
  criticalPathApi: {
    getCriticalPath: vi.fn(),
  },
  aiApi: {
    getDependencySuggestions: vi.fn(),
    acceptSuggestion: vi.fn(),
  },
  ApiClientError: class ApiClientError extends Error {
    public status: number;
    public code: string;
    constructor(err: { status: number; code: string; message: string }) {
      super(err.message);
      this.status = err.status;
      this.code = err.code;
    }
  },
}));

describe('useProjectBoard hook', () => {
  const initialTask: Task = {
    id: 'task-1',
    projectId: 'p-1',
    title: 'Design API',
    workflowStatus: 'BACKLOG',
    dependencyStatus: 'READY',
    durationDays: 2,
    version: 1,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('rolls back optimistic task move if backend rejects the update', async () => {
    vi.mocked(taskApi.getTasksByProject).mockResolvedValue([initialTask]);
    vi.mocked(dependencyApi.getDependenciesByProject).mockResolvedValue([]);
    vi.mocked(taskApi.updateTask).mockRejectedValue(new Error('Backend rejected move'));

    const { result } = renderHook(() => useProjectBoard('p-1'));

    // Wait for initial load
    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.tasks[0].workflowStatus).toBe('BACKLOG');

    // Attempt to move to IN_PROGRESS
    let moveSuccess = false;
    await act(async () => {
      moveSuccess = await result.current.moveTask('task-1', 'IN_PROGRESS');
    });

    expect(moveSuccess).toBe(false);
    // Verified UI rollback to original status
    expect(result.current.tasks[0].workflowStatus).toBe('BACKLOG');
    // Clear user error displayed
    expect(result.current.actionError).toContain('Unable to update task');
  });

  it('reconciles authoritative server state upon successful movement', async () => {
    const updatedServerTask: Task = {
      ...initialTask,
      workflowStatus: 'IN_PROGRESS',
      version: 2,
    };

    vi.mocked(taskApi.getTasksByProject)
      .mockResolvedValueOnce([initialTask])
      .mockResolvedValueOnce([updatedServerTask]);
    vi.mocked(dependencyApi.getDependenciesByProject).mockResolvedValue([]);
    vi.mocked(taskApi.updateTask).mockResolvedValue(updatedServerTask);

    const { result } = renderHook(() => useProjectBoard('p-1'));

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.tasks[0].workflowStatus).toBe('BACKLOG');

    let moveSuccess = false;
    await act(async () => {
      moveSuccess = await result.current.moveTask('task-1', 'IN_PROGRESS');
    });

    expect(moveSuccess).toBe(true);
    expect(result.current.tasks[0].workflowStatus).toBe('IN_PROGRESS');
    expect(result.current.actionError).toBeNull();
  });
});
