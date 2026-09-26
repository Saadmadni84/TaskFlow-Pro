'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Task,
  TaskStatus,
  TaskDependency,
  CreateTaskRequest,
  UpdateTaskRequest,
  CreateDependencyRequest,
  ScheduleImpactPreviewResponse,
  CriticalPathResponse,
} from '@/types';
import {
  taskApi,
  dependencyApi,
  schedulingApi,
  criticalPathApi,
  aiApi,
  ApiClientError,
} from '@/lib/api';

export function useProjectBoard(projectId: string | null) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [dependencies, setDependencies] = useState<TaskDependency[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Fetch all tasks and dependencies for project
  const loadData = useCallback(async () => {
    if (!projectId) {
      setTasks([]);
      setDependencies([]);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const [fetchedTasks, fetchedDeps] = await Promise.all([
        taskApi.getTasksByProject(projectId),
        dependencyApi.getDependenciesByProject(projectId),
      ]);
      setTasks(fetchedTasks);
      setDependencies(fetchedDeps);
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        setError(err.message);
      } else {
        setError('Failed to load project board data');
      }
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Derived: map of taskId -> prerequisite task IDs and dependent task IDs
  const dependencyMap = useMemo(() => {
    const prerequisitesByTask = new Map<string, string[]>();
    const dependentsByTask = new Map<string, string[]>();

    for (const dep of dependencies) {
      // dep.predecessorTaskId -> dep.successorTaskId
      // successor has prerequisite predecessor
      const existingPrereqs = prerequisitesByTask.get(dep.successorTaskId) || [];
      prerequisitesByTask.set(dep.successorTaskId, [...existingPrereqs, dep.predecessorTaskId]);

      // predecessor has dependent successor
      const existingDeps = dependentsByTask.get(dep.predecessorTaskId) || [];
      dependentsByTask.set(dep.predecessorTaskId, [...existingDeps, dep.successorTaskId]);
    }

    return { prerequisitesByTask, dependentsByTask };
  }, [dependencies]);

  // Optimistic Move Task
  const moveTask = async (taskId: string, targetStatus: TaskStatus): Promise<boolean> => {
    const originalTasks = [...tasks];
    const taskToMove = originalTasks.find(t => t.id === taskId);
    if (!taskToMove || taskToMove.workflowStatus === targetStatus) {
      return false;
    }

    // 1. Optimistic UI update
    setTasks(prev =>
      prev.map(t => (t.id === taskId ? { ...t, workflowStatus: targetStatus } : t))
    );
    setActionError(null);

    // 2. Call backend mutation
    try {
      const updatePayload: UpdateTaskRequest = {
        title: taskToMove.title,
        description: taskToMove.description,
        workflowStatus: targetStatus,
        plannedStartDate: taskToMove.plannedStartDate || taskToMove.startDate,
        startDate: taskToMove.startDate,
        dueDate: taskToMove.dueDate,
        durationDays: taskToMove.durationDays,
      };

      await taskApi.updateTask(taskId, updatePayload);

      // 3. Reconcile with authoritative server state
      // Moving a task (especially to/from DONE) triggers readiness updates across successors
      await loadData();
      return true;
    } catch (err: unknown) {
      // 4. Rollback UI on failure
      setTasks(originalTasks);
      if (err instanceof ApiClientError) {
        if (err.status === 409 || err.code === 'CONCURRENCY_CONFLICT') {
          setActionError('This task was updated elsewhere. Refresh to load the latest version.');
        } else {
          setActionError(err.message || 'Unable to update task. The server rejected the change. Your board has been restored.');
        }
      } else {
        setActionError('Unable to update task. The server rejected the change. Your board has been restored.');
      }
      return false;
    }
  };

  // Create Task
  const createTask = async (payload: CreateTaskRequest): Promise<Task | null> => {
    setActionError(null);
    try {
      const created = await taskApi.createTask(payload);
      // Re-fetch project board so schedule and readiness are fully synchronized
      await loadData();
      return created;
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to create task');
      }
      return null;
    }
  };

  // Update Task
  const updateTask = async (taskId: string, payload: UpdateTaskRequest): Promise<Task | null> => {
    setActionError(null);
    try {
      const updated = await taskApi.updateTask(taskId, payload);
      await loadData();
      return updated;
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        if (err.status === 409 || err.code === 'CONCURRENCY_CONFLICT') {
          setActionError('This task was updated elsewhere. Refresh to load the latest version.');
        } else {
          setActionError(err.message);
        }
      } else {
        setActionError('Failed to update task');
      }
      return null;
    }
  };

  // Delete Task
  const deleteTask = async (taskId: string): Promise<boolean> => {
    setActionError(null);
    try {
      await taskApi.deleteTask(taskId);
      await loadData();
      return true;
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        setActionError(err.message || 'Cannot delete task due to dependency constraints');
      } else {
        setActionError('Failed to delete task');
      }
      return false;
    }
  };

  // Add Dependency
  const addDependency = async (
    predecessorTaskId: string,
    successorTaskId: string
  ): Promise<{ success: boolean; error?: string }> => {
    setActionError(null);
    try {
      const request: CreateDependencyRequest = {
        predecessorTaskId,
        successorTaskId,
      };
      await dependencyApi.createDependency(request);
      await loadData();
      return { success: true };
    } catch (err: unknown) {
      let message = 'Failed to add dependency';
      if (err instanceof ApiClientError) {
        if (err.code === 'CYCLE_DETECTED' || err.message?.toLowerCase().includes('cycle')) {
          message = 'Dependency not added. This dependency would create a cycle in the workflow.';
        } else {
          message = err.message;
        }
      }
      setActionError(message);
      return { success: false, error: message };
    }
  };

  // Remove Dependency
  const removeDependency = async (
    predecessorTaskId: string,
    successorTaskId: string
  ): Promise<boolean> => {
    setActionError(null);
    try {
      await dependencyApi.deleteDependency(predecessorTaskId, successorTaskId);
      await loadData();
      return true;
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to remove dependency');
      }
      return false;
    }
  };

  // Schedule Preview
  const previewScheduleImpact = async (
    taskId: string,
    plannedStartDate: string
  ): Promise<ScheduleImpactPreviewResponse> => {
    return schedulingApi.previewScheduleImpact(taskId, { plannedStartDate });
  };

  // Critical Path Analysis
  const getCriticalPath = async (): Promise<CriticalPathResponse | null> => {
    if (!projectId) return null;
    try {
      return await criticalPathApi.getCriticalPath(projectId);
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to calculate critical path');
      }
      return null;
    }
  };

  // Accept AI Suggestion
  const acceptAiSuggestion = async (
    predecessorTaskId: string,
    successorTaskId: string
  ): Promise<boolean> => {
    setActionError(null);
    try {
      await aiApi.acceptSuggestion({ predecessorTaskId, successorTaskId });
      await loadData();
      return true;
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to accept AI suggestion');
      }
      return false;
    }
  };

  return {
    tasks,
    dependencies,
    prerequisitesByTask: dependencyMap.prerequisitesByTask,
    dependentsByTask: dependencyMap.dependentsByTask,
    loading,
    error,
    actionError,
    clearActionError: () => setActionError(null),
    refresh: loadData,
    moveTask,
    createTask,
    updateTask,
    deleteTask,
    addDependency,
    removeDependency,
    previewScheduleImpact,
    getCriticalPath,
    acceptAiSuggestion,
  };
}
