'use client';

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { projectApi, taskApi, dependencyApi, ApiClientError } from '@/lib/api';
import { Project, Task, TaskDependency } from '@/types';
import { formatDateRange, formatCalendarDate } from '@/lib/utils/dates';

export default function SchedulingPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [tasks, setTasks] = useState<Task[]>([]);
  const [dependencies, setDependencies] = useState<TaskDependency[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<string>('');
  const [plannedStartDate, setPlannedStartDate] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [propagating, setPropagating] = useState<boolean>(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Load all projects
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
      setErrorMessage('Failed to connect to backend service. Ensure Spring Boot is running.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  // Load tasks & dependencies for selected project
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

      if (fetchedTasks.length > 0) {
        setSelectedTaskId((prev) => {
          const exists = fetchedTasks.some((t) => t.id === prev);
          return exists ? prev : fetchedTasks[0].id;
        });
      } else {
        setSelectedTaskId('');
      }
    } catch (err: unknown) {
      console.error('Failed to load project tasks', err);
      setErrorMessage('Failed to load tasks and schedule data from the backend.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedProjectId) {
      loadProjectData(selectedProjectId);
    }
  }, [selectedProjectId, loadProjectData]);

  // Sync plannedStartDate when selectedTask changes
  const selectedTask = useMemo(() => {
    return tasks.find((t) => t.id === selectedTaskId) || null;
  }, [tasks, selectedTaskId]);

  useEffect(() => {
    if (selectedTask) {
      setPlannedStartDate(selectedTask.plannedStartDate || selectedTask.startDate || '');
    }
  }, [selectedTask]);

  // Predecessors map for tasks
  const predecessorsByTask = useMemo(() => {
    const map = new Map<string, string[]>();
    dependencies.forEach((dep) => {
      const list = map.get(dep.successorTaskId) || [];
      list.push(dep.predecessorTaskId);
      map.set(dep.successorTaskId, list);
    });
    return map;
  }, [dependencies]);


  // Quick shift helper
  const handleShiftDays = (days: number) => {
    if (!plannedStartDate) return;
    const current = new Date(plannedStartDate);
    current.setDate(current.getDate() + days);
    setPlannedStartDate(current.toISOString().split('T')[0]);
  };

  // Execute backend schedule propagation
  const handlePropagateSchedule = async () => {
    if (!selectedTask || !plannedStartDate) return;
    try {
      setPropagating(true);
      setErrorMessage(null);
      setSuccessMessage(null);

      await taskApi.updateTask(selectedTask.id, {
        title: selectedTask.title,
        description: selectedTask.description,
        workflowStatus: selectedTask.workflowStatus,
        plannedStartDate: plannedStartDate,
        startDate: plannedStartDate,
        dueDate: selectedTask.dueDate,
        durationDays: selectedTask.durationDays,
      });

      setSuccessMessage(`Schedule propagation committed. Downstream tasks recalculated according to DAG topological constraints.`);
      await loadProjectData(selectedProjectId);
    } catch (err: unknown) {
      console.error('Failed to propagate schedule', err);
      if (err instanceof ApiClientError) {
        setErrorMessage(err.message || 'Backend rejected schedule modification.');
      } else {
        setErrorMessage('Failed to execute schedule propagation on backend.');
      }
    } finally {
      setPropagating(false);
    }
  };

  const selectedProject = projects.find((p) => p.id === selectedProjectId) || null;

  return (
    <div className="max-w-6xl mx-auto space-y-8 pb-12">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 5 Scheduling Engine
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Deterministic Constraint-Based Scheduling</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Dependency-Aware Scheduling Engine
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          TaskFlow Pro enforces mathematical constraint-based schedule recalculation.
          Tasks maintain independent <code className="text-zinc-200">plannedStartDate</code> baselines, duration is strictly preserved,
          and downstream delays propagate in topological order without compounding across converging graph paths.
        </p>
      </div>

      {/* Error & Success Feedback */}
      {errorMessage && (
        <div className="p-3.5 rounded-lg border border-rose-500/40 bg-rose-950/40 text-rose-300 text-xs flex items-center justify-between">
          <span>{errorMessage}</span>
          <button onClick={() => setErrorMessage(null)} className="text-zinc-400 hover:text-zinc-200 text-xs">✕</button>
        </div>
      )}
      {successMessage && (
        <div className="p-3.5 rounded-lg border border-emerald-500/40 bg-emerald-950/40 text-emerald-300 text-xs flex items-center justify-between">
          <span>{successMessage}</span>
          <button onClick={() => setSuccessMessage(null)} className="text-zinc-400 hover:text-zinc-200 text-xs">✕</button>
        </div>
      )}

      {/* Project Selector Bar */}
      <Card className="p-4 bg-zinc-900/80 border-zinc-800 space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <label htmlFor="sched-project-select" className="text-xs font-mono text-zinc-400 uppercase shrink-0">
              Active Project:
            </label>
            {loading && projects.length === 0 ? (
              <Skeleton className="h-8 w-60" />
            ) : (
              <select
                id="sched-project-select"
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

          <Button
            variant="outline"
            size="sm"
            onClick={() => selectedProjectId && loadProjectData(selectedProjectId)}
            disabled={loading || !selectedProjectId}
            className="text-xs"
          >
            Refresh Schedules
          </Button>
        </div>

        {selectedProject && (
          <div className="pt-2 border-t border-zinc-800/80 flex flex-wrap items-center justify-between text-[11px] font-mono text-zinc-500 gap-2">
            <div>
              Project: <code className="text-zinc-400">{selectedProject.name}</code> ({tasks.length} Tasks, {dependencies.length} Edges)
            </div>
            <div className="flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
              <span>Spring Boot SchedulingService Active</span>
            </div>
          </div>
        )}
      </Card>

      {/* Live Interactive Propagation Control Card */}
      <Card className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-zinc-800/80 pb-3 gap-2">
          <div>
            <h2 className="text-sm font-medium text-zinc-200">
              Interactive Schedule Mutation &amp; Non-Compounding Propagation
            </h2>
            <p className="text-xs text-zinc-400">
              Select a task, modify its planned date, and execute real topological propagation backed by the server engine
            </p>
          </div>
          <span className="text-xs font-mono text-emerald-400 bg-emerald-950/40 border border-emerald-500/20 px-2 py-0.5 rounded">
            Live Backend Engine
          </span>
        </div>

        {/* Mutation Controls */}
        <div className="p-4 rounded-lg bg-zinc-950/60 border border-zinc-800/80 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 items-end">
            <div className="space-y-1.5">
              <label htmlFor="target-task-select" className="block text-xs font-mono uppercase text-zinc-400">
                Source Task to Shift:
              </label>
              <select
                id="target-task-select"
                value={selectedTaskId}
                onChange={(e) => setSelectedTaskId(e.target.value)}
                className="w-full bg-zinc-900 border border-zinc-700/80 rounded-md px-3 py-2 text-xs text-zinc-100 focus:outline-none focus:border-emerald-500"
              >
                {tasks.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.title} ({t.durationDays}d)
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label htmlFor="planned-start-input" className="block text-xs font-mono uppercase text-zinc-400">
                Proposed Planned Start:
              </label>
              <input
                id="planned-start-input"
                type="date"
                value={plannedStartDate}
                onChange={(e) => setPlannedStartDate(e.target.value)}
                className="w-full bg-zinc-900 border border-zinc-700/80 rounded-md px-3 py-1.5 text-xs text-zinc-100 focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div className="flex items-center gap-2">
              <Button
                variant="primary"
                size="sm"
                onClick={handlePropagateSchedule}
                disabled={propagating || !selectedTask}
                className="w-full text-xs h-9"
              >
                {propagating ? 'Propagating...' : 'Propagate Schedule Update'}
              </Button>
            </div>
          </div>

          {/* Quick Shift Presets */}
          <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-zinc-800/60 text-xs">
            <span className="text-[11px] font-mono text-zinc-500 uppercase">Quick Adjust:</span>
            <button
              onClick={() => handleShiftDays(1)}
              className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-zinc-300 hover:bg-zinc-800 text-xs"
            >
              +1 Day
            </button>
            <button
              onClick={() => handleShiftDays(3)}
              className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-zinc-300 hover:bg-zinc-800 text-xs"
            >
              +3 Days
            </button>
            <button
              onClick={() => handleShiftDays(7)}
              className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-zinc-300 hover:bg-zinc-800 text-xs"
            >
              +7 Days
            </button>
            <button
              onClick={() => handleShiftDays(-3)}
              className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-zinc-300 hover:bg-zinc-800 text-xs"
            >
              -3 Days
            </button>
            {selectedTask && (
              <button
                onClick={() => setPlannedStartDate(selectedTask.plannedStartDate || selectedTask.startDate || '')}
                className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-zinc-400 hover:text-zinc-200 text-xs ml-auto"
              >
                Reset to Current
              </button>
            )}
          </div>
        </div>

        {/* Live Project Schedule Table */}
        <div className="space-y-3">
          <div className="flex items-center justify-between text-xs text-zinc-400">
            <span className="font-semibold text-zinc-200">Current Authoritative Schedules in DAG</span>
            <span className="font-mono text-[11px] text-zinc-500">{tasks.length} Tasks Scheduled</span>
          </div>

          {loading && tasks.length === 0 ? (
            <div className="p-4 space-y-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : (
            <div className="overflow-x-auto rounded border border-zinc-800/80">
              <table className="w-full text-left text-xs">
                <thead className="bg-zinc-950 text-zinc-400 font-mono text-[11px] border-b border-zinc-800 uppercase">
                  <tr>
                    <th className="py-2.5 px-3">Task</th>
                    <th className="py-2.5 px-3">Duration</th>
                    <th className="py-2.5 px-3">Planned Start</th>
                    <th className="py-2.5 px-3">Scheduled Dates</th>
                    <th className="py-2.5 px-3">Constraint Rule</th>
                    <th className="py-2.5 px-3 text-right">Readiness</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-800/60 bg-zinc-900/30">
                  {tasks.map((t) => {
                    const preds = predecessorsByTask.get(t.id) || [];
                    const isSelected = t.id === selectedTaskId;

                    return (
                      <tr
                        key={t.id}
                        className={`hover:bg-zinc-800/30 transition-colors ${
                          isSelected ? 'bg-zinc-800/40' : ''
                        }`}
                      >
                        <td className="py-2.5 px-3 max-w-xs">
                          <div className="font-medium text-zinc-200 truncate flex items-center gap-2">
                            <span>{t.title}</span>
                            {isSelected && (
                              <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-emerald-950 text-emerald-400 border border-emerald-500/30">
                                Target
                              </span>
                            )}
                          </div>
                          <div className="text-[10px] font-mono text-zinc-500">
                            ID: {t.id.substring(0, 8)}...
                          </div>
                        </td>
                        <td className="py-2.5 px-3 font-mono text-zinc-300">
                          {t.durationDays}d
                        </td>
                        <td className="py-2.5 px-3 font-mono text-zinc-400">
                          {formatCalendarDate(t.plannedStartDate || t.startDate)}
                        </td>
                        <td className="py-2.5 px-3 font-mono">
                          <span className="text-zinc-200">
                            {formatDateRange(
                              t.scheduledStartDate || t.startDate,
                              t.scheduledDueDate || t.dueDate
                            )}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-zinc-400 text-[11px]">
                          {preds.length === 0 ? (
                            <span className="font-mono text-zinc-500 text-[10px]">
                              Planned Start Dominant
                            </span>
                          ) : (
                            <span className="font-mono text-amber-400/90 text-[10px]">
                              max(predDue + 1) [{preds.length} prereq{preds.length > 1 ? 's' : ''}]
                            </span>
                          )}
                        </td>
                        <td className="py-2.5 px-3 text-right">
                          <Badge variant={t.dependencyStatus === 'READY' ? 'ready' : 'blocked'}>
                            {t.dependencyStatus}
                          </Badge>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Technical Callout */}
        <div className="p-4 rounded-lg bg-zinc-900/60 border border-zinc-800 text-xs text-zinc-300 leading-relaxed space-y-1">
          <strong className="text-zinc-100 font-medium">Authoritative Scheduling Invariant:</strong>
          <p>
            TaskFlow Pro enforces strict mathematical constraint scheduling: <code className="text-emerald-400">scheduledStartDate = max(plannedStartDate, max(predDue + 1))</code>.
            Because downstream delays propagate in topological order without compounding across converging graph paths, tasks dependent on multiple parallel branches
            wait only for the latest predecessor to finish.
          </p>
        </div>
      </Card>
    </div>
  );
}
