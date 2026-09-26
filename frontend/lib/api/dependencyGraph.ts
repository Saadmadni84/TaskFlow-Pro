import { apiClient } from './client';
import { DependencyGraph } from '@/types';

export const dependencyGraphApi = {
  getDependencyGraph: async (projectId: string): Promise<DependencyGraph> => {
    return apiClient.get<DependencyGraph>(`/projects/${projectId}/dependency-graph`);
  },
};
