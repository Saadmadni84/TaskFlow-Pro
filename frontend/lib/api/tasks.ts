import { apiClient } from './client';
import { Task, CreateTaskRequest, UpdateTaskRequest } from '@/types';

export const taskApi = {
  getTasksByProject: async (projectId: string): Promise<Task[]> => {
    return apiClient.get<Task[]>(`/tasks/project/${projectId}`);
  },

  getTask: async (id: string): Promise<Task> => {
    return apiClient.get<Task>(`/tasks/${id}`);
  },

  createTask: async (data: CreateTaskRequest): Promise<Task> => {
    return apiClient.post<Task>('/tasks', data);
  },

  updateTask: async (id: string, data: UpdateTaskRequest): Promise<Task> => {
    return apiClient.put<Task>(`/tasks/${id}`, data);
  },

  deleteTask: async (id: string): Promise<void> => {
    return apiClient.delete<void>(`/tasks/${id}`);
  },
};
