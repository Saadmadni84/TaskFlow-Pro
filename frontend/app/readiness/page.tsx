'use client';

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { projectApi, taskApi, dependencyApi, ApiClientError } from '@/lib/api';
import { Project, Task, TaskDependency, TaskStatus } from '@/types';

export default function ReadinessPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [tasks, setTasks] = useState<Task[]>([]);
  const [dependencies, setDependencies] = useState<TaskDependency[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [updatingTaskId, setUpdatingTaskId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Fetch projects from backend
  const loadProjects = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMessage(null);
      const data = await projectApi.getProjects();
      setProjects(data);
      if (data.length > 0) {
        setSelectedProjectId((prev) => prev || data[0].id);
      }
    } catch (err: unknown) {
      console.error('Failed to load projects', err);
      setErrorMessage('Failed to connect to backend service. Ensure Spring Boot is running on port 8080.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  // Load project tasks & dependencies from real backend
  const loadProjectData = useCallback(async (projectId: string) => {
    if (!projectId) return;
    try {
      setLoading(true);
      setErrorMessage(null);
      const [fetchedTasks, fetchedDeps] = await Promise.all([
        taskApi.getTasksByProject(projectId),
        dependencyApi.getDependenciesByProject(projectId),
      ]);
      setTasks(fetchedTasks);
      setDependencies(fetchedDeps);
    } catch (err: unknown) {
      console.error('Failed to load project tasks', err);
      setErrorMessage('Failed to load tasks and dependencies from the backend engine.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedProjectId) {
      loadProjectData(selectedProjectId);
    }
  }, [selectedProjectId, loadProjectData]);

  // Build map of prerequisite tasks for display
  const taskMap = useMemo(() => {
    const map = new Map<string, Task>();
    tasks.forEach((t) => map.set(t.id, t));
    return map;
  }, [tasks]);

  const prerequisitesByTaskId = useMemo(() => {
    const map = new Map<string, string[]>();
    dependencies.forEach((dep) => {
      const existing = map.get(dep.successorTaskId) || [];
      existing.push(dep.predecessorTaskId);
      map.set(dep.successorTaskId, existing);
    });
    return map;
  }, [dependencies]);

  // Toggle task status via authoritative backend API
  const handleToggleTaskStatus = async (task: Task) => {
    const isDone = task.workflowStatus === 'DONE';
    const nextStatus: TaskStatus = isDone ? 'IN_PROGRESS' : 'DONE';

    // Client-side guard: A BLOCKED task cannot transition to DONE
    if (!isDone && task.dependencyStatus === 'BLOCKED') {
      setErrorMessage(`Task "${task.title}" is BLOCKED. All prerequisite tasks must reach DONE before this task can be completed.`);
      return;
    }

    try {
      setUpdatingTaskId(task.id);
      setErrorMessage(null);

      // Mutate strictly via backend TaskService -> DependencyReadinessService -> Database
      await taskApi.updateTask(task.id, {
        title: task.title,
        description: task.description,
        workflowStatus: nextStatus,
        plannedStartDate: task.plannedStartDate || task.startDate,
        startDate: task.startDate,
        dueDate: task.dueDate,
        durationDays: task.durationDays,
      });

      // Reload authoritative server state to receive derived readiness updates across successors
      await loadProjectData(selectedProjectId);
    } catch (err: unknown) {
      console.error('Failed to update task status', err);
      if (err instanceof ApiClientError) {
        setErrorMessage(err.message || 'The backend rejected the status transition.');
      } else {
        setErrorMessage('Failed to update task status on the server.');
      }
    } finally {
      setUpdatingTaskId(null);
    }
  };

  const selectedProject = projects.find((p) => p.id === selectedProjectId) || null;

  return (
    <div className="max-w-6xl mx-auto space-y-8 pb-12">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 4 Readiness Engine
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Derived Dependency State Propagation</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Dependency Readiness Engine
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          In TaskFlow Pro, <code className="text-emerald-400">READY</code> and <code className="text-rose-400">BLOCKED</code> are 
          authoritative server-derived states calculated by the Spring Boot readiness engine by evaluating whether all prerequisite tasks
          in the DAG have reached <code className="text-zinc-200">DONE</code>. Downstream tasks automatically unlock or roll back in topological order.
        </p>
      </div>

      {/* Error alert if any */}
      {errorMessage && (
        <div className="p-3.5 rounded-lg border border-rose-500/40 bg-rose-950/40 text-rose-300 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-rose-400 shrink-0" />
            <span>{errorMessage}</span>
          </div>
          <button
            onClick={() => setErrorMessage(null)}
            className="text-zinc-400 hover:text-zinc-200 text-xs font-mono ml-4"
          >
            ✕
          </button>
        </div>
      )}

      {/* Real Backend Project Selector */}
      <Card className="p-4 bg-zinc-900/80 border-zinc-800 space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <label htmlFor="readiness-project-select" className="text-xs font-mono text-zinc-400 uppercase shrink-0">
              Active Project:
            </label>
            {loading && projects.length === 0 ? (
              <Skeleton className="h-8 w-60" />
            ) : (
              <select
                id="readiness-project-select"
                value={selectedProjectId}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                className="bg-zinc-950 border border-zinc-700/80 rounded-md px-3 py-1.5 text-xs text-zinc-200 font-medium focus:outline-none focus:border-emerald-500 min-w-[260px]"
              >
                {projects.map((proj) => (
                  <option key={proj.id} value={proj.id}>
                    {proj.name}
                  </option>
                ))}
              </select>
            )}
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => selectedProjectId && loadProjectData(selectedProjectId)}
              disabled={loading || !selectedProjectId}
              className="text-xs"
            >
              Refresh Engine State
            </Button>
          </div>
        </div>

        {selectedProject && (
          <div className="pt-2 border-t border-zinc-800/80 flex flex-wrap items-center justify-between text-[11px] font-mono text-zinc-500 gap-2">
            <div>
              Project UUID: <code className="text-zinc-400">{selectedProject.id}</code>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
              <span>Backed by PostgreSQL + DependencyReadinessService</span>
            </div>
          </div>
        )}
      </Card>

      {/* Interactive Live Propagation Simulator */}
      <Card className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-zinc-800/80 pb-3 gap-2">
          <div>
            <h2 className="text-sm font-medium text-zinc-200">
              Live Readiness Propagation Workspace
            </h2>
            <p className="text-xs text-zinc-400">
              Toggle completion states to trigger authoritative backend readiness propagation and topological rollback
            </p>
          </div>
          <span className="text-xs font-mono text-emerald-400 bg-emerald-950/40 border border-emerald-500/20 px-2 py-0.5 rounded">
            Authoritative Server State
          </span>
        </div>

        {/* Task Cards Grid */}
        {loading && tasks.length === 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, idx) => (
              <div key={idx} className="p-4 rounded-lg border border-zinc-800 bg-zinc-950/60 space-y-3">
                <Skeleton className="h-5 w-24" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-8 w-full" />
              </div>
            ))}
          </div>
        ) : tasks.length === 0 ? (
          <div className="p-8 text-center text-zinc-500 text-xs">
            No tasks found in this project.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {tasks.map((task) => {
              const isDone = task.workflowStatus === 'DONE';
              const isBlocked = task.dependencyStatus === 'BLOCKED';
              const isReady = task.dependencyStatus === 'READY';
              const predIds = prerequisitesByTaskId.get(task.id) || [];
              const isUpdating = updatingTaskId === task.id;

              return (
                <div
                  key={task.id}
                  className={`p-4 rounded-lg border transition-all flex flex-col justify-between ${
                    isDone
                      ? 'border-zinc-800 bg-zinc-900/40'
                      : isReady
                      ? 'border-emerald-500/40 bg-emerald-950/20 shadow-sm'
                      : 'border-rose-950/80 bg-zinc-950/80'
                  }`}
                >
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-mono text-zinc-400">
                        {task.id.substring(0, 8)}...
                      </span>
                      <div className="flex items-center gap-1.5">
                        <Badge variant={isDone ? 'ready' : isReady ? 'ready' : 'blocked'}>
                          {isDone ? 'DONE' : isReady ? 'READY' : 'BLOCKED'}
                        </Badge>
                      </div>
                    </div>

                    <div className="text-sm font-semibold text-zinc-100">{task.title}</div>

                    {task.description && (
                      <p className="text-xs text-zinc-400 line-clamp-2 leading-relaxed">
                        {task.description}
                      </p>
                    )}

                    {/* Prerequisites Info */}
                    <div className="text-xs text-zinc-400 pt-1">
                      {predIds.length === 0 ? (
                        <span className="text-zinc-500 text-[11px] font-mono">
                          Root task (no prerequisites)
                        </span>
                      ) : (
                        <div className="space-y-1">
                          <span className="text-[11px] font-mono text-zinc-400">
                            Prerequisites ({predIds.length}):
                          </span>
                          <div className="flex flex-wrap gap-1">
                            {predIds.map((pid) => {
                              const pred = taskMap.get(pid);
                              const predDone = pred?.workflowStatus === 'DONE';
                              return (
                                <span
                                  key={pid}
                                  className={`text-[10px] font-mono px-1.5 py-0.5 rounded border ${
                                    predDone
                                      ? 'bg-emerald-950/60 border-emerald-500/30 text-emerald-300'
                                      : 'bg-rose-950/60 border-rose-500/30 text-rose-300'
                                  }`}
                                  title={pred ? `${pred.title} (${pred.workflowStatus})` : pid}
                                >
                                  {pred ? pred.title.substring(0, 18) : pid.substring(0, 6)}... {predDone ? '✓' : '✗'}
                                </span>
                              );
                            })}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Actions Bar */}
                  <div className="pt-4 mt-4 border-t border-zinc-800/80 space-y-2">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-zinc-400">
                        Workflow: <strong className="text-zinc-200">{task.workflowStatus}</strong>
                      </span>
                      <span className="font-mono text-[10px] text-zinc-500">
                        v{task.version ?? 0}
                      </span>
                    </div>

                    <div className="space-y-1">
                      <Button
                        size="sm"
                        variant={isDone ? 'outline' : isBlocked ? 'secondary' : 'primary'}
                        disabled={isUpdating || (!isDone && isBlocked)}
                        onClick={() => handleToggleTaskStatus(task)}
                        className={`w-full text-xs flex items-center justify-center gap-1.5 ${
                          !isDone && isBlocked
                            ? 'opacity-50 cursor-not-allowed bg-zinc-800/60 text-zinc-500 border border-zinc-700/40'
                            : ''
                        }`}
                        title={
                          !isDone && isBlocked
                            ? 'Cannot complete: prerequisites must be completed first'
                            : undefined
                        }
                      >
                        {isUpdating ? (
                          <span>Updating...</span>
                        ) : isDone ? (
                          <span>Reopen Task (→ IN_PROGRESS)</span>
                        ) : isBlocked ? (
                          <span>Mark Done (Blocked)</span>
                        ) : (
                          <span>Mark Done (→ DONE)</span>
                        )}
                      </Button>

                      {/* Explicit Explanatory Notice for Blocked Task */}
                      {!isDone && isBlocked && (
                        <p className="text-[11px] text-rose-400/90 text-center font-medium leading-tight pt-1">
                          Prerequisites must be completed first before marking this task as DONE.
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Engine Rules Reference */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2">
          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">Authoritative Prerequisite Rule</span>
            <p className="text-[11px] text-zinc-400">
              A task is READY if and only if every direct predecessor has achieved <code className="text-zinc-300">workflowStatus == DONE</code>.
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">Automatic Rollback</span>
            <p className="text-[11px] text-zinc-400">
              Reopening an upstream task immediately rolls back all downstream descendants to BLOCKED in topological order.
            </p>
          </div>

          <div className="p-3 rounded border border-zinc-800/60 bg-zinc-950/30 space-y-1">
            <span className="text-xs text-zinc-300 font-medium">Server-Derived Safety</span>
            <p className="text-[11px] text-zinc-400">
              Clients cannot set <code className="text-zinc-300">dependencyStatus</code>. Any attempt to mark a BLOCKED task as DONE is rejected with HTTP 409 Conflict.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}
