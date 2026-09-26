import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AiSuggestionsModal } from '@/components/ai/AiSuggestionsModal';
import { aiApi } from '@/lib/api';
import { Task } from '@/types';

vi.mock('@/lib/api', () => ({
  aiApi: {
    getDependencySuggestions: vi.fn(),
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

describe('AiSuggestionsModal', () => {
  const mockTask: Task = {
    id: 'task-target',
    projectId: 'p-1',
    title: 'Implement Checkout API',
    workflowStatus: 'BACKLOG',
    dependencyStatus: 'READY',
    durationDays: 3,
    version: 0,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders AI suggestions with confidence, explanation, and clear trust label', async () => {
    vi.mocked(aiApi.getDependencySuggestions).mockResolvedValue({
      targetTaskId: 'task-target',
      suggestionCount: 1,
      suggestions: [
        {
          predecessor: { taskId: 'task-pred', title: 'Design Payment Schema' },
          successor: { taskId: 'task-target', title: 'Implement Checkout API' },
          confidence: 0.91,
          reason: 'The API likely depends on the database schema.',
        },
      ],
    });

    const onAccept = vi.fn().mockResolvedValue(true);

    render(
      <AiSuggestionsModal
        isOpen={true}
        task={mockTask}
        onClose={vi.fn()}
        onAcceptSuggestion={onAccept}
      />
    );

    await waitFor(() => {
      expect(screen.getByText(/Design Payment Schema/i)).toBeInTheDocument();
      expect(screen.getByText('91% confidence')).toBeInTheDocument();
      expect(
        screen.getByText(/The API likely depends on the database schema/i)
      ).toBeInTheDocument();
      expect(screen.getByText(/AI Suggestion — Review before adding/i)).toBeInTheDocument();
    });

    // Accept suggestion
    const acceptBtn = screen.getByRole('button', { name: /Accept Suggestion/i });
    fireEvent.click(acceptBtn);

    await waitFor(() => {
      expect(onAccept).toHaveBeenCalledWith('task-pred', 'task-target');
    });
  });

  it('displays graceful fallback when AI suggestions are unavailable', async () => {
    vi.mocked(aiApi.getDependencySuggestions).mockRejectedValue(
      new Error('AI service down')
    );

    render(
      <AiSuggestionsModal
        isOpen={true}
        task={mockTask}
        onClose={vi.fn()}
        onAcceptSuggestion={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(
        screen.getByText('AI suggestions are currently unavailable.')
      ).toBeInTheDocument();
      expect(
        screen.getByText(/The board and manual dependency creation remain fully functional/i)
      ).toBeInTheDocument();
    });
  });
});
