/**
 * Core domain types for TaskFlow Pro
 * Matching backend contracts from Phases 1–8.
 */

export type TaskStatus = 'BACKLOG' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';

export type DependencyStatus = 'READY' | 'BLOCKED';

export interface Project {
  id: string;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
}

export interface Task {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  workflowStatus: TaskStatus;
  dependencyStatus: DependencyStatus;
  plannedStartDate?: string; // YYYY-MM-DD
  scheduledStartDate?: string; // YYYY-MM-DD
  scheduledDueDate?: string; // YYYY-MM-DD
  startDate?: string;
  dueDate?: string;
  durationDays: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTaskRequest {
  projectId: string;
  title: string;
  description?: string;
  workflowStatus?: TaskStatus;
  plannedStartDate?: string;
  startDate?: string;
  dueDate?: string;
  durationDays?: number;
}

export interface UpdateTaskRequest {
  title: string;
  description?: string;
  workflowStatus?: TaskStatus;
  plannedStartDate?: string;
  startDate?: string;
  dueDate?: string;
  durationDays?: number;
}

export interface TaskDependency {
  id: string;
  predecessorTaskId: string;
  successorTaskId: string;
  createdAt: string;
}

export interface CreateDependencyRequest {
  predecessorTaskId: string;
  successorTaskId: string;
}

export type ImpactType = 'DELAYED' | 'UNCHANGED' | 'SHIFTED_EARLIER';

export type ReasonType = 
  | 'DIRECT_CHANGE' 
  | 'BINDING_PREDECESSOR' 
  | 'CONVERGING_PATHS' 
  | 'UNCHANGED';

export interface TaskScheduleImpact {
  taskId: string;
  title: string;
  currentPlannedStart?: string;
  proposedPlannedStart?: string;
  currentScheduledStart?: string;
  proposedScheduledStart?: string;
  currentScheduledDue?: string;
  proposedScheduledDue?: string;
  durationDays: number;
  startShiftDays: number;
  dueShiftDays: number;
  shiftDays: number;
  impactType: ImpactType;
  reasonType: ReasonType;
  constraintSourceTaskIds: string[];
  constraintDate?: string;
  reason: string;
}

export interface ScheduleImpactSummary {
  affectedTaskCount: number;
  changedTaskCount: number;
  unchangedTaskCount: number;
  maximumDelayDays: number;
}

export interface ScheduleImpactPreviewResponse {
  sourceTaskId: string;
  summary: ScheduleImpactSummary;
  tasks: TaskScheduleImpact[];
}

export interface SchedulePreviewRequest {
  plannedStartDate: string; // YYYY-MM-DD
}

export interface TaskSummary {
  taskId: string;
  title: string;
}

export type DependencySuggestionValidationStatus =
  | 'VALID'
  | 'DUPLICATE'
  | 'SELF_DEPENDENCY'
  | 'CYCLE'
  | 'INVALID_TASK'
  | 'INVALID_PROJECT';

export interface DependencySuggestion {
  predecessor: TaskSummary;
  successor: TaskSummary;
  confidence: number;
  reason: string;
  validationStatus?: DependencySuggestionValidationStatus;
}

export interface DependencySuggestionResponse {
  targetTaskId: string;
  suggestionCount: number;
  suggestions: DependencySuggestion[];
}

export interface AcceptSuggestionRequest {
  predecessorTaskId: string;
  successorTaskId: string;
}

export interface TaskMetrics {
  taskId: string;
  title: string;
  durationDays: number;
  earliestStart: string;
  earliestFinish: string;
  latestStart: string;
  latestFinish: string;
  totalSlackDays: number;
  isCritical: boolean;
}

export interface CriticalPathResponse {
  projectId: string;
  projectStartDate: string | null;
  projectCompletionDate: string | null;
  projectDurationDays?: number;
  criticalTaskCount?: number;
  criticalPathCount?: number;
  criticalTaskIds: string[];
  criticalPaths: string[][];
  tasks: TaskMetrics[];
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
  requestId?: string;
  details?: ApiValidationErrorDetail[];
}

export interface DependencyGraphNode {
  id: string;
  title: string;
  workflowStatus: TaskStatus;
  dependencyStatus: DependencyStatus;
  scheduledStartDate?: string;
  scheduledDueDate?: string;
  plannedStartDate?: string;
  durationDays?: number;
}

export interface DependencyGraphEdge {
  id: string;
  predecessorTaskId: string;
  successorTaskId: string;
}

export interface DependencyGraph {
  projectId: string;
  nodes: DependencyGraphNode[];
  edges: DependencyGraphEdge[];
}

