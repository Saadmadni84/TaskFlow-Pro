import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DependencyGraphView } from '@/components/graph/DependencyGraphView';
import { dependencyGraphApi } from '@/lib/api';

vi.mock('@/lib/api', () => ({
  dependencyGraphApi: {
    getDependencyGraph: vi.fn(),
  },
  dependencyApi: {
    createDependency: vi.fn(),
    deleteDependency: vi.fn(),
  },
}));

describe('DependencyGraphView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading state initially', async () => {
    (dependencyGraphApi.getDependencyGraph as any).mockReturnValue(new Promise(() => {}));

    render(<DependencyGraphView projectId="p-1" />);

    expect(screen.getByText(/loading project dependency graph/i)).toBeInTheDocument();
  });

  it('renders empty state when project has no tasks', async () => {
    (dependencyGraphApi.getDependencyGraph as any).mockResolvedValue({
      projectId: 'p-1',
      nodes: [],
      edges: [],
    });

    render(<DependencyGraphView projectId="p-1" />);

    await waitFor(() => {
      expect(screen.getByText('No tasks yet')).toBeInTheDocument();
    });
  });

  it('renders error state when graph query fails', async () => {
    (dependencyGraphApi.getDependencyGraph as any).mockRejectedValue(
      new Error('Project not found with identifier: p-1')
    );

    render(<DependencyGraphView projectId="p-1" />);

    await waitFor(() => {
      expect(screen.getByText('Unable to load the dependency graph')).toBeInTheDocument();
      expect(screen.getByText('Project not found with identifier: p-1')).toBeInTheDocument();
    });
  });

  it('renders toolbar with task counts and toggles accessible list view', async () => {
    const mockGraph = {
      projectId: 'p-1',
      nodes: [
        {
          id: 'task-a',
          title: 'Design API',
          workflowStatus: 'DONE' as const,
          dependencyStatus: 'READY' as const,
          scheduledStartDate: '2026-06-01',
          scheduledDueDate: '2026-06-03',
          durationDays: 3,
        },
        {
          id: 'task-b',
          title: 'Implement Backend',
          workflowStatus: 'BACKLOG' as const,
          dependencyStatus: 'BLOCKED' as const,
          scheduledStartDate: '2026-06-04',
          scheduledDueDate: '2026-06-08',
          durationDays: 5,
        },
      ],
      edges: [
        {
          id: 'e-1',
          predecessorTaskId: 'task-a',
          successorTaskId: 'task-b',
        },
      ],
    };

    (dependencyGraphApi.getDependencyGraph as any).mockResolvedValue(mockGraph);

    render(<DependencyGraphView projectId="p-1" />);

    await waitFor(() => {
      expect(screen.getByText('2 Tasks')).toBeInTheDocument();
      expect(screen.getByText('1 Dependencies')).toBeInTheDocument();
    });

    // Toggle Accessible List View
    const toggleBtn = screen.getByTitle('Toggle between graphical canvas and accessible list view');
    fireEvent.click(toggleBtn);

    // Verify accessible list content
    expect(screen.getByText('Topological Task Relationships (Accessible)')).toBeInTheDocument();
    expect(screen.getAllByText('Design API').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Implement Backend').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Prerequisites \(1\):/)).toBeInTheDocument();
  });

  it('auto-selects candidate task and toggles chain mode when chain button is clicked', async () => {
    const mockGraph = {
      projectId: 'p-1',
      nodes: [
        {
          id: 'task-a',
          title: 'Design API',
          workflowStatus: 'DONE' as const,
          dependencyStatus: 'READY' as const,
          durationDays: 3,
        },
        {
          id: 'task-b',
          title: 'Implement Backend',
          workflowStatus: 'BACKLOG' as const,
          dependencyStatus: 'BLOCKED' as const,
          durationDays: 5,
        },
      ],
      edges: [
        {
          id: 'e-1',
          predecessorTaskId: 'task-a',
          successorTaskId: 'task-b',
        },
      ],
    };

    (dependencyGraphApi.getDependencyGraph as any).mockResolvedValue(mockGraph);

    render(<DependencyGraphView projectId="p-1" />);

    await waitFor(() => {
      expect(screen.getByText('2 Tasks')).toBeInTheDocument();
    });

    const chainBtn = screen.getByRole('button', { name: /chain:/i });
    expect(chainBtn).toHaveTextContent('Chain: Direct');

    // Click chain button without prior selection -> auto selects task with edges and toggles to Full (Ancestors)
    fireEvent.click(chainBtn);

    await waitFor(() => {
      expect(chainBtn).toHaveTextContent('Chain: Full (Ancestors)');
      expect(screen.getAllByText(/Focus:/i).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('selects and clears task focus using dropdown', async () => {
    const mockGraph = {
      projectId: 'p-1',
      nodes: [
        {
          id: 'task-a',
          title: 'Design API',
          workflowStatus: 'DONE' as const,
          dependencyStatus: 'READY' as const,
          durationDays: 3,
        },
        {
          id: 'task-b',
          title: 'Implement Backend',
          workflowStatus: 'BACKLOG' as const,
          dependencyStatus: 'BLOCKED' as const,
          durationDays: 5,
        },
      ],
      edges: [
        {
          id: 'e-1',
          predecessorTaskId: 'task-a',
          successorTaskId: 'task-b',
        },
      ],
    };

    (dependencyGraphApi.getDependencyGraph as any).mockResolvedValue(mockGraph);

    render(<DependencyGraphView projectId="p-1" />);

    await waitFor(() => {
      expect(screen.getByText('2 Tasks')).toBeInTheDocument();
    });

    const select = screen.getByLabelText(/select task to inspect dependency chain/i);
    fireEvent.change(select, { target: { value: 'task-b' } });

    await waitFor(() => {
      expect(screen.getAllByText(/Focus:/i).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByTitle('Clear selected task focus')).toBeInTheDocument();
    });

    // Clear focus
    fireEvent.click(screen.getByTitle('Clear selected task focus'));

    await waitFor(() => {
      expect(screen.queryByTitle('Clear selected task focus')).not.toBeInTheDocument();
    });
  });
});
