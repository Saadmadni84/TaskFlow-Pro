import { apiClient } from './client';
import { TaskDependency, CreateDependencyRequest } from '@/types';

export const dependencyApi = {
  getDependenciesByProject: async (projectId: string): Promise<TaskDependency[]> => {
    return apiClient.get<TaskDependency[]>(`/dependencies/project/${projectId}`);
  },

  getDependenciesByPredecessor: async (taskId: string): Promise<TaskDependency[]> => {
    return apiClient.get<TaskDependency[]>(`/dependencies/predecessor/${taskId}`);
  },

  getDependenciesBySuccessor: async (taskId: string): Promise<TaskDependency[]> => {
    return apiClient.get<TaskDependency[]>(`/dependencies/successor/${taskId}`);
  },

  createDependency: async (data: CreateDependencyRequest): Promise<TaskDependency> => {
    return apiClient.post<TaskDependency>('/dependencies', data);
  },

  deleteDependency: async (predecessorTaskId: string, successorTaskId: string): Promise<void> => {
    return apiClient.delete<void>(`/dependencies/${predecessorTaskId}/${successorTaskId}`);
  },
};
