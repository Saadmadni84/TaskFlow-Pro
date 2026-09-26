import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CriticalPathWorkspace } from '@/components/criticalpath/CriticalPathWorkspace';
import { criticalPathApi } from '@/lib/api';
import { Project, CriticalPathResponse } from '@/types';

// Mock criticalPathApi
vi.mock('@/lib/api', () => ({
  criticalPathApi: {
    getCriticalPath: vi.fn(),
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

const mockProjects: Project[] = [
  { id: 'proj-1', name: 'TaskFlow Pro Demo', description: 'Core demo', createdAt: '', updatedAt: '' },
];

vi.mock('@/hooks/useProjects', () => ({
  useProjects: () => ({
    projects: mockProjects,
    selectedProjectId: 'proj-1',
    selectedProject: mockProjects[0],
    setSelectedProjectId: vi.fn(),
    loading: false,
    error: null,
    createProject: vi.fn(),
    refreshProjects: vi.fn(),
  }),
}));

const mockCpmResponse: CriticalPathResponse = {
  projectId: 'proj-1',
  projectStartDate: '2026-09-01',
  projectCompletionDate: '2026-09-18',
  projectDurationDays: 18,
  criticalTaskCount: 3,
  criticalPathCount: 2,
  criticalTaskIds: ['task-a', 'task-b', 'task-d', 'task-c'],
  criticalPaths: [
    ['task-a', 'task-b', 'task-d'], // Path 1
    ['task-a', 'task-c', 'task-d'], // Path 2 (converging equal duration)
  ],
  tasks: [
    {
      taskId: 'task-a',
      title: 'Design Database',
      durationDays: 3,
      earliestStart: '2026-09-01',
      earliestFinish: '2026-09-03',
      latestStart: '2026-09-01',
      latestFinish: '2026-09-03',
      totalSlackDays: 0,
      isCritical: true,
    },
    {
      taskId: 'task-b',
      title: 'Backend API',
      durationDays: 5,
      earliestStart: '2026-09-04',
      earliestFinish: '2026-09-08',
      latestStart: '2026-09-04',
      latestFinish: '2026-09-08',
      totalSlackDays: 0,
      isCritical: true,
    },
    {
      taskId: 'task-c',
      title: 'Frontend UI',
      durationDays: 5,
      earliestStart: '2026-09-04',
      earliestFinish: '2026-09-08',
      latestStart: '2026-09-04',
      latestFinish: '2026-09-08',
      totalSlackDays: 0,
      isCritical: true,
    },
    {
      taskId: 'task-d',
      title: 'Integration Testing',
      durationDays: 4,
      earliestStart: '2026-09-09',
      earliestFinish: '2026-09-12',
      latestStart: '2026-09-09',
      latestFinish: '2026-09-12',
      totalSlackDays: 0,
      isCritical: true,
    },
    {
      taskId: 'task-e',
      title: 'Documentation',
      durationDays: 2,
      earliestStart: '2026-09-04',
      earliestFinish: '2026-09-05',
      latestStart: '2026-09-11',
      latestFinish: '2026-09-12',
      totalSlackDays: 7,
      isCritical: false,
    },
  ],
};

describe('CriticalPathWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders project summary cards, multiple critical paths, and task breakdown table', async () => {
    vi.mocked(criticalPathApi.getCriticalPath).mockResolvedValue(mockCpmResponse);

    render(<CriticalPathWorkspace />);

    // Wait for analysis to load
    await waitFor(() => {
      // Summary cards
      expect(screen.getByText('Project Duration')).toBeInTheDocument();
      expect(screen.getByText('Project Finish')).toBeInTheDocument();
      expect(screen.getByText('Critical Tasks')).toBeInTheDocument();
      expect(screen.getByText('Critical Paths')).toBeInTheDocument();
      expect(screen.getAllByText(/Sep 18, 2026/i).length).toBeGreaterThan(0);
      expect(screen.getByText(/parallel paths/i)).toBeInTheDocument();
    });

    // Check multiple critical paths are rendered
    expect(screen.getByText(/Critical Path 1 \(3 tasks\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Critical Path 2 \(3 tasks\)/i)).toBeInTheDocument();

    // Check tasks appear in table
    expect(screen.getAllByText('Design Database').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Backend API').length).toBeGreaterThan(0);
    expect(screen.getByText('Documentation')).toBeInTheDocument();

    // Check critical and float badges
    expect(screen.getAllByText('CRITICAL').length).toBeGreaterThan(0);
    expect(screen.getByText('FLOAT')).toBeInTheDocument();
    expect(screen.getByText('7d')).toBeInTheDocument(); // 7 days slack
  });

  it('allows clicking a non-critical task to inspect slack explanation', async () => {
    vi.mocked(criticalPathApi.getCriticalPath).mockResolvedValue(mockCpmResponse);

    render(<CriticalPathWorkspace />);

    await waitFor(() => {
      expect(screen.getByText('Documentation')).toBeInTheDocument();
    });

    // Click Documentation row
    const docRow = screen.getByText('Documentation').closest('tr');
    expect(docRow).not.toBeNull();
    fireEvent.click(docRow!);

    await waitFor(() => {
      expect(screen.getByText(/Scheduling Flexibility:/i)).toBeInTheDocument();
      expect(screen.getByText(/This task can move by up to/i)).toBeInTheDocument();
      expect(screen.getAllByText(/7 days/i).length).toBeGreaterThan(0);
    });
  });

  it('allows filtering by critical and float tasks', async () => {
    vi.mocked(criticalPathApi.getCriticalPath).mockResolvedValue(mockCpmResponse);

    render(<CriticalPathWorkspace />);

    await waitFor(() => {
      expect(screen.getByText('Documentation')).toBeInTheDocument();
    });

    // Filter to Critical only
    const criticalFilterBtn = screen.getByRole('button', { name: /Critical \(4\)/i });
    fireEvent.click(criticalFilterBtn);

    // Documentation should no longer appear in the table body
    expect(screen.queryByRole('cell', { name: /Documentation/i })).not.toBeInTheDocument();

    // Filter to Float only
    const floatFilterBtn = screen.getByRole('button', { name: /Float \(1\)/i });
    fireEvent.click(floatFilterBtn);

    // Documentation should reappear
    expect(screen.getByRole('cell', { name: /Documentation/i })).toBeInTheDocument();
    // Design Database should be hidden
    expect(screen.queryByRole('cell', { name: /Design Database/i })).not.toBeInTheDocument();
  });

  it('handles empty project state cleanly without crashing', async () => {
    vi.mocked(criticalPathApi.getCriticalPath).mockResolvedValue({
      projectId: 'proj-1',
      projectStartDate: null,
      projectCompletionDate: null,
      criticalTaskIds: [],
      criticalPaths: [],
      tasks: [],
    });

    render(<CriticalPathWorkspace />);

    await waitFor(() => {
      expect(screen.getByText(/No tasks available for Critical Path Analysis\./i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Go to Kanban Workspace/i })).toBeInTheDocument();
    });
  });

  it('handles all independent tasks (no dependencies) notice', async () => {
    vi.mocked(criticalPathApi.getCriticalPath).mockResolvedValue({
      projectId: 'proj-1',
      projectStartDate: '2026-09-01',
      projectCompletionDate: '2026-09-05',
      criticalTaskIds: ['task-x'],
      criticalPaths: [], // No dependency chains
      tasks: [
        {
          taskId: 'task-x',
          title: 'Independent Task 1',
          durationDays: 5,
          earliestStart: '2026-09-01',
          earliestFinish: '2026-09-05',
          latestStart: '2026-09-01',
          latestFinish: '2026-09-05',
          totalSlackDays: 0,
          isCritical: true,
        },
        {
          taskId: 'task-y',
          title: 'Independent Task 2',
          durationDays: 2,
          earliestStart: '2026-09-01',
          earliestFinish: '2026-09-02',
          latestStart: '2026-09-04',
          latestFinish: '2026-09-05',
          totalSlackDays: 3,
          isCritical: false,
        },
      ],
    });

    render(<CriticalPathWorkspace />);

    await waitFor(() => {
      expect(screen.getByText(/Independent Tasks:/i)).toBeInTheDocument();
      expect(screen.getByText(/All tasks are currently independent\./i)).toBeInTheDocument();
    });
  });

  it('handles error state and allows retry', async () => {
    vi.mocked(criticalPathApi.getCriticalPath).mockRejectedValueOnce(new Error('Network timeout'));

    render(<CriticalPathWorkspace />);

    await waitFor(() => {
      expect(screen.getByText(/Unable to calculate the critical path\./i)).toBeInTheDocument();
      expect(screen.getByText('Network timeout')).toBeInTheDocument();
    });

    // Retry
    vi.mocked(criticalPathApi.getCriticalPath).mockResolvedValue(mockCpmResponse);
    fireEvent.click(screen.getByRole('button', { name: /Try Again/i }));

    await waitFor(() => {
      expect(screen.getAllByText('Design Database').length).toBeGreaterThan(0);
    });
  });
});
