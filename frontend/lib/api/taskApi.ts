import { apiClient } from './client';
import { Task, ApiSuccessResponse } from '@/types';

/**
 * Task API service module.
 * Endpoint calls are isolated from React presentation components.
 */
export const taskApi = {
  getAll: async (): Promise<Task[]> => {
    const response = await apiClient.get<ApiSuccessResponse<Task[]>>('/tasks');
    return response.data;
  },

  getById: async (id: string): Promise<Task> => {
    const response = await apiClient.get<ApiSuccessResponse<Task>>(`/tasks/${encodeURIComponent(id)}`);
    return response.data;
  },
};
