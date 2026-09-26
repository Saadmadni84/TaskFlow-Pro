import { apiClient } from './client';
import {
  DependencySuggestionResponse,
  AcceptSuggestionRequest,
  TaskDependency,
} from '@/types';

export const aiApi = {
  getDependencySuggestions: async (
    taskId: string,
    limit?: number
  ): Promise<DependencySuggestionResponse> => {
    const query = limit ? `?limit=${limit}` : '';
    return apiClient.post<DependencySuggestionResponse>(
      `/tasks/${taskId}/dependency-suggestions${query}`
    );
  },

  acceptSuggestion: async (
    request: AcceptSuggestionRequest
  ): Promise<TaskDependency> => {
    return apiClient.post<TaskDependency>(
      '/dependency-suggestions/accept',
      request
    );
  },
};
