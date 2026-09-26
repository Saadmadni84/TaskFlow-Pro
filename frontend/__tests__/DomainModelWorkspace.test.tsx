import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { DomainModelWorkspace } from '@/components/domain/DomainModelWorkspace';
import { projectApi, taskApi, dependencyApi } from '@/lib/api';
import { Project, Task, TaskDependency } from '@/types';

// Mock API modules
vi.mock('@/lib/api', () => ({
  projectApi: {
    getProjects: vi.fn(),
  },
  taskApi: {
    getTasksByProject: vi.fn(),
  },
  dependencyApi: {
    getDependenciesByProject: vi.fn(),
  },
}));

const mockProjects: Project[] = [
  {
    id: 'proj-1',
    name: 'TaskFlow Pro Core Platform',
    description: 'Core project workspace',
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
];

const mockTasks: Task[] = [
  {
    id: 'task-1',
    projectId: 'proj-1',
    title: 'Requirements & API Contract Analysis',
    description: 'Contract analysis',
    workflowStatus: 'DONE',
    dependencyStatus: 'READY',
    durationDays: 3,
    version: 0,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
  {
    id: 'task-2',
    projectId: 'proj-1',
    title: 'Backend API & Core Engine Development',
    description: 'Build backend engine',
    workflowStatus: 'IN_PROGRESS',
    dependencyStatus: 'READY',
    durationDays: 5,
    version: 1,
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

describe('DomainModelWorkspace (Phase 2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (projectApi.getProjects as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockProjects);
    (taskApi.getTasksByProject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockTasks);
    (dependencyApi.getDependenciesByProject as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockDependencies);
  });

  it('renders header, database connection status, and loads domain entities', async () => {
    render(<DomainModelWorkspace />);

    expect(screen.getByText('Core Domain Model & Persistence Layer')).toBeInTheDocument();
    expect(screen.getByText('PostgreSQL 16 • Connected')).toBeInTheDocument();
    expect(screen.getByText('Flyway V1–V5 Migrations Applied')).toBeInTheDocument();

    await waitFor(() => {
      expect(projectApi.getProjects).toHaveBeenCalledTimes(1);
      expect(taskApi.getTasksByProject).toHaveBeenCalledWith('proj-1');
      expect(dependencyApi.getDependenciesByProject).toHaveBeenCalledWith('proj-1');
    });

    // Check stats
    await waitFor(() => {
      expect(screen.getByText('TaskFlow Pro Core Platform')).toBeInTheDocument();
      expect(screen.getByText('Requirements & API Contract Analysis')).toBeInTheDocument();
      expect(screen.getByText('Backend API & Core Engine Development')).toBeInTheDocument();
    });
  });

  it('switches to Dependencies tab and displays directed edges', async () => {
    render(<DomainModelWorkspace />);

    await waitFor(() => {
      expect(screen.getByText('Requirements & API Contract Analysis')).toBeInTheDocument();
    });

    const depsTab = screen.getByRole('button', { name: /Dependencies \(1\)/i });
    fireEvent.click(depsTab);

    await waitFor(() => {
      expect(screen.getByText('CHECK pred <> succ')).toBeInTheDocument();
      expect(screen.getByText('UNIQUE(pred, succ)')).toBeInTheDocument();
    });
  });

  it('switches to Projects tab and shows root aggregate details', async () => {
    render(<DomainModelWorkspace />);

    await waitFor(() => {
      expect(screen.getByText('Requirements & API Contract Analysis')).toBeInTheDocument();
    });

    const projectsTab = screen.getByRole('button', { name: /Projects \(1\)/i });
    fireEvent.click(projectsTab);

    await waitFor(() => {
      expect(screen.getByText('Active')).toBeInTheDocument();
    });
  });
});
