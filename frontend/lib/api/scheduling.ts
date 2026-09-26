import { apiClient } from './client';
import { ScheduleImpactPreviewResponse, SchedulePreviewRequest } from '@/types';

export const schedulingApi = {
  previewScheduleImpact: async (
    taskId: string,
    request: SchedulePreviewRequest
  ): Promise<ScheduleImpactPreviewResponse> => {
    return apiClient.post<ScheduleImpactPreviewResponse>(
      `/tasks/${taskId}/schedule/preview`,
      request
    );
  },
};
