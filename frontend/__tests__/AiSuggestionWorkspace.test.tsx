import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AiSuggestionWorkspace } from '@/components/ai/AiSuggestionWorkspace';
import { aiApi, ApiClientError } from '@/lib/api';
import { Task, Project } from '@/types';

// Mock API and hooks
vi.mock('@/lib/api', () => ({
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

const mockProjects: Project[] = [
  { id: 'proj-1', name: 'TaskFlow Core', description: 'Core project', createdAt: '', updatedAt: '' },
];

const mockTasks: Task[] = [
  {
    id: 'task-1',
    projectId: 'proj-1',
    title: 'Design Database Schema',
    description: 'PostgreSQL tables and constraints',
    workflowStatus: 'DONE',
    dependencyStatus: 'READY',
    durationDays: 2,
    version: 1,
    createdAt: '',
    updatedAt: '',
  },
  {
    id: 'task-2',
    projectId: 'proj-1',
    title: 'Implement Backend API',
    description: 'REST endpoints for workflow engine',
    workflowStatus: 'IN_PROGRESS',
    dependencyStatus: 'BLOCKED',
    durationDays: 4,
    version: 1,
    createdAt: '',
    updatedAt: '',
  },
];

const mockAcceptAiSuggestion = vi.fn();

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

vi.mock('@/hooks/useProjectBoard', () => ({
  useProjectBoard: () => ({
    tasks: mockTasks,
    dependencies: [],
    prerequisitesByTask: new Map(),
    dependentsByTask: new Map(),
    loading: false,
    error: null,
    actionError: null,
    clearActionError: vi.fn(),
    refresh: vi.fn(),
    acceptAiSuggestion: mockAcceptAiSuggestion,
  }),
}));

describe('AiSuggestionWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders initial state with task selection placeholder and concept cards', () => {
    render(<AiSuggestionWorkspace />);

    expect(screen.getByText('AI Dependency Suggestions')).toBeInTheDocument();
    expect(screen.getByText(/Select a task to get AI dependency suggestions/i)).toBeInTheDocument();
    expect(screen.getByText('Advisory Only')).toBeInTheDocument();
    expect(screen.getByText('DAG Engine Validation')).toBeInTheDocument();
    expect(screen.getByText('Human Approval')).toBeInTheDocument();
  });

  it('displays target task context card when a task is selected', async () => {
    render(<AiSuggestionWorkspace />);

    const select = screen.getByLabelText(/Target task/i);
    fireEvent.change(select, { target: { value: 'task-2' } });

    await waitFor(() => {
      expect(screen.getByText('Target Task Context')).toBeInTheDocument();
      expect(screen.getByText('Implement Backend API')).toBeInTheDocument();
      expect(screen.getByText('REST endpoints for workflow engine')).toBeInTheDocument();
      expect(screen.getByText(/None — this task currently has no prerequisite dependencies/i)).toBeInTheDocument();
    });
  });

  it('generates suggestions and renders candidate cards with confidence and direction', async () => {
    vi.mocked(aiApi.getDependencySuggestions).mockResolvedValue({
      targetTaskId: 'task-2',
      suggestionCount: 1,
      suggestions: [
        {
          predecessor: { taskId: 'task-1', title: 'Design Database Schema' },
          successor: { taskId: 'task-2', title: 'Implement Backend API' },
          confidence: 0.88,
          reason: 'Backend APIs depend on the database schema.',
        },
      ],
    });

    render(<AiSuggestionWorkspace />);

    const select = screen.getByLabelText(/Target task/i);
    fireEvent.change(select, { target: { value: 'task-2' } });

    const generateBtn = screen.getByRole('button', { name: /Generate AI Suggestions/i });
    fireEvent.click(generateBtn);

    await waitFor(() => {
      expect(aiApi.getDependencySuggestions).toHaveBeenCalledWith('task-2');
      expect(screen.getByText('Confidence: 88%')).toBeInTheDocument();
      expect(screen.getByText(/Backend APIs depend on the database schema/i)).toBeInTheDocument();
      expect(screen.getByText('AI confidence is advisory. Review before accepting.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Accept' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Dismiss' })).toBeInTheDocument();
    });
  });

  it('accepts suggestion through deterministic dependency engine with visual confirmation', async () => {
    vi.mocked(aiApi.getDependencySuggestions).mockResolvedValue({
      targetTaskId: 'task-2',
      suggestionCount: 1,
      suggestions: [
        {
          predecessor: { taskId: 'task-1', title: 'Design Database Schema' },
          successor: { taskId: 'task-2', title: 'Implement Backend API' },
          confidence: 0.88,
          reason: 'Database schema needed before backend API.',
        },
      ],
    });

    mockAcceptAiSuggestion.mockResolvedValue(true);

    render(<AiSuggestionWorkspace />);

    const select = screen.getByLabelText(/Target task/i);
    fireEvent.change(select, { target: { value: 'task-2' } });

    fireEvent.click(screen.getByRole('button', { name: /Generate AI Suggestions/i }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Accept' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Accept' }));

    await waitFor(() => {
      expect(mockAcceptAiSuggestion).toHaveBeenCalledWith('task-1', 'task-2');
      expect(screen.getByText('✓ Dependency added')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Accepted' })).toBeDisabled();
    });
  });

  it('dismisses suggestion from the active view without mutating dependencies', async () => {
    vi.mocked(aiApi.getDependencySuggestions).mockResolvedValue({
      targetTaskId: 'task-2',
      suggestionCount: 1,
      suggestions: [
        {
          predecessor: { taskId: 'task-1', title: 'Design Database Schema' },
          successor: { taskId: 'task-2', title: 'Implement Backend API' },
          confidence: 0.88,
          reason: 'Database schema needed before backend API.',
        },
      ],
    });

    render(<AiSuggestionWorkspace />);

    const select = screen.getByLabelText(/Target task/i);
    fireEvent.change(select, { target: { value: 'task-2' } });

    fireEvent.click(screen.getByRole('button', { name: /Generate AI Suggestions/i }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Dismiss' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));

    await waitFor(() => {
      expect(screen.queryByText(/Database schema needed before backend API/i)).not.toBeInTheDocument();
      expect(mockAcceptAiSuggestion).not.toHaveBeenCalled();
    });
  });

  it('handles empty results gracefully with a retry option', async () => {
    vi.mocked(aiApi.getDependencySuggestions).mockResolvedValue({
      targetTaskId: 'task-2',
      suggestionCount: 0,
      suggestions: [],
    });

    render(<AiSuggestionWorkspace />);

    const select = screen.getByLabelText(/Target task/i);
    fireEvent.change(select, { target: { value: 'task-2' } });

    fireEvent.click(screen.getByRole('button', { name: /Generate AI Suggestions/i }));

    await waitFor(() => {
      expect(screen.getByText('No dependency suggestions found.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Generate Again' })).toBeInTheDocument();
    });
  });

  it('handles AI disabled state gracefully with informational notice', async () => {
    const err = new ApiClientError({
      status: 503,
      code: 'AI_DISABLED',
      message: 'AI dependency suggestions are currently disabled',
    });

    vi.mocked(aiApi.getDependencySuggestions).mockRejectedValue(err);

    render(<AiSuggestionWorkspace />);

    const select = screen.getByLabelText(/Target task/i);
    fireEvent.change(select, { target: { value: 'task-2' } });

    fireEvent.click(screen.getByRole('button', { name: /Generate AI Suggestions/i }));

    await waitFor(() => {
      expect(screen.getByText('AI suggestions are currently disabled.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Open Kanban Board' })).toBeInTheDocument();
    });
  });
});
