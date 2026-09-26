import { apiClient } from './client';
import { Project, CreateProjectRequest } from '@/types';

export const projectApi = {
  getProjects: async (): Promise<Project[]> => {
    return apiClient.get<Project[]>('/projects');
  },

  getProject: async (id: string): Promise<Project> => {
    return apiClient.get<Project>(`/projects/${id}`);
  },

  createProject: async (data: CreateProjectRequest): Promise<Project> => {
    return apiClient.post<Project>('/projects', data);
  },

  deleteProject: async (id: string): Promise<void> => {
    return apiClient.delete<void>(`/projects/${id}`);
  },
};
