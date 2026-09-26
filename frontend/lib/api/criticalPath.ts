import { apiClient } from './client';
import { CriticalPathResponse } from '@/types';

export const criticalPathApi = {
  getCriticalPath: async (projectId: string): Promise<CriticalPathResponse> => {
    return apiClient.get<CriticalPathResponse>(`/projects/${projectId}/critical-path`);
  },
};
