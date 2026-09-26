/**
 * Core domain types for TaskFlow Pro
 */

export type TaskStatus = 'BACKLOG' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';

export type ReadinessStatus = 'READY' | 'BLOCKED';

export interface Task {
  id: string;
  title: string;
  description?: string;
  status: TaskStatus;
  readinessStatus: ReadinessStatus;
  startDate?: string;
  dueDate?: string;
  durationDays?: number;
  isCriticalPath?: boolean;
  createdAt: string;
  updatedAt: string;
}

export type DependencyType = 'FINISH_TO_START';

export interface Dependency {
  id: string;
  predecessorTaskId: string;
  successorTaskId: string;
  type: DependencyType;
  createdAt: string;
}

export interface ApiSuccessResponse<T> {
  timestamp: string;
  status: number;
  message: string;
  data: T;
}

export interface ApiValidationErrorDetail {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  details?: ApiValidationErrorDetail[];
}
