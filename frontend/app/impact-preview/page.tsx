'use client';

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { projectApi, taskApi, schedulingApi, ApiClientError } from '@/lib/api';
import { Project, Task, ScheduleImpactPreviewResponse } from '@/types';
import { formatDateRange } from '@/lib/utils/dates';

export default function ImpactPreviewPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [tasks, setTasks] = useState<Task[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<string>('');
  const [proposedDate, setProposedDate] = useState<string>('');
  const [loadingProjects, setLoadingProjects] = useState<boolean>(true);
  const [loadingTasks, setLoadingTasks] = useState<boolean>(false);
  const [loadingPreview, setLoadingPreview] = useState<boolean>(false);
  const [committing, setCommitting] = useState<boolean>(false);
  const [previewData, setPreviewData] = useState<ScheduleImpactPreviewResponse | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Load all projects
  const loadProjects = useCallback(async () => {
    try {
      setLoadingProjects(true);
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
      setLoadingProjects(false);
    }
  }, []);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  // Core backend simulation runner
  const runPreviewForTask = useCallback(async (taskId: string, date: string) => {
    if (!taskId || !date) return;
    try {
      setLoadingPreview(true);
      setErrorMessage(null);
      const response = await schedulingApi.previewScheduleImpact(taskId, {
        plannedStartDate: date,
      });
      setPreviewData(response);
    } catch (err: unknown) {
      console.error('Failed to calculate impact preview', err);
      if (err instanceof ApiClientError) {
        setErrorMessage(err.message || 'Backend rejected preview calculation.');
      } else {
        setErrorMessage('Failed to calculate schedule impact preview on backend.');
      }
    } finally {
      setLoadingPreview(false);
    }
  }, []);

  // Load project tasks
  const loadProjectTasks = useCallback(async (projectId: string) => {
    if (!projectId) return;
    try {
      setLoadingTasks(true);
      setErrorMessage(null);
      const fetchedTasks = await taskApi.getTasksByProject(projectId);
      setTasks(fetchedTasks);

      if (fetchedTasks.length > 0) {
        const first = fetchedTasks[0];
        setSelectedTaskId(first.id);
        const base = first.plannedStartDate || first.startDate || new Date().toISOString().split('T')[0];
        setProposedDate(base);
        await runPreviewForTask(first.id, base);
      } else {
        setSelectedTaskId('');
        setProposedDate('');
        setPreviewData(null);
      }
    } catch (err: unknown) {
      console.error('Failed to load project tasks', err);
      setErrorMessage('Failed to load tasks from the backend.');
    } finally {
      setLoadingTasks(false);
    }
  }, [runPreviewForTask]);

  useEffect(() => {
    if (selectedProjectId) {
      loadProjectTasks(selectedProjectId);
    }
  }, [selectedProjectId, loadProjectTasks]);

  const selectedTask = useMemo(() => {
    return tasks.find((t) => t.id === selectedTaskId) || null;
  }, [tasks, selectedTaskId]);

  const handleSelectTask = (taskId: string) => {
    setSelectedTaskId(taskId);
    const task = tasks.find((t) => t.id === taskId);
    if (task) {
      const base = task.plannedStartDate || task.startDate || new Date().toISOString().split('T')[0];
      setProposedDate(base);
      runPreviewForTask(taskId, base);
    }
  };

  const handleDateChange = (date: string) => {
    setProposedDate(date);
    if (selectedTaskId && date) {
      runPreviewForTask(selectedTaskId, date);
    }
  };

  // Shift helper
  const handleShiftDays = (days: number) => {
    if (!proposedDate || !selectedTaskId) return;
    const current = new Date(proposedDate);
    current.setDate(current.getDate() + days);
    const newDate = current.toISOString().split('T')[0];
    setProposedDate(newDate);
    runPreviewForTask(selectedTaskId, newDate);
  };

  // Run backend impact preview simulation button
  const handleRunPreview = () => {
    if (selectedTaskId && proposedDate) {
      runPreviewForTask(selectedTaskId, proposedDate);
    }
  };

  // Commit proposed change to backend
  const handleCommitChange = async () => {
    if (!selectedTask || !proposedDate) return;
    try {
      setCommitting(true);
      setErrorMessage(null);
      setSuccessMessage(null);

      await taskApi.updateTask(selectedTask.id, {
        title: selectedTask.title,
        description: selectedTask.description,
        workflowStatus: selectedTask.workflowStatus,
        plannedStartDate: proposedDate,
        startDate: proposedDate,
        dueDate: selectedTask.dueDate,
        durationDays: selectedTask.durationDays,
      });

      setSuccessMessage(`Schedule mutation committed to database. Downstream schedules updated in topological order.`);
      await loadProjectTasks(selectedProjectId);
    } catch (err: unknown) {
      console.error('Failed to commit schedule change', err);
      if (err instanceof ApiClientError) {
        setErrorMessage(err.message || 'Backend rejected schedule commit.');
      } else {
        setErrorMessage('Failed to commit schedule modification.');
      }
    } finally {
      setCommitting(false);
    }
  };

  const selectedProject = projects.find((p) => p.id === selectedProjectId) || null;

  return (
    <div className="max-w-6xl mx-auto space-y-8 pb-12">
      {/* Overview Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Phase 6 Impact Preview
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Deterministic Side-Effect-Free Simulation</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          Dependency Impact Preview Simulator
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          Before committing a schedule mutation, TaskFlow Pro runs a side-effect-free in-memory simulation
          using the authoritative backend calculation engine. It predicts downstream schedule changes,
          identifies binding predecessors, and explains exactly why shifts occur.
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
            <label htmlFor="impact-project-select" className="text-xs font-mono text-zinc-400 uppercase shrink-0">
              Workspace Aggregate:
            </label>
            {loadingProjects ? (
              <Skeleton className="h-8 w-60" />
            ) : (
              <select
                id="impact-project-select"
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
            onClick={() => selectedProjectId && loadProjectTasks(selectedProjectId)}
            disabled={loadingTasks || !selectedProjectId}
            className="text-xs"
          >
            Refresh Records
          </Button>
        </div>

        {selectedProject && (
          <div className="pt-2 border-t border-zinc-800/80 flex flex-wrap items-center justify-between text-[11px] font-mono text-zinc-500 gap-2">
            <div>
              Project: <code className="text-zinc-400">{selectedProject.name}</code> ({tasks.length} Available Tasks)
            </div>
            <div className="flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
              <span>Endpoint: POST /api/tasks/{'{id}'}/schedule/preview</span>
            </div>
          </div>
        )}
      </Card>

      {/* Control Panel Card */}
      <Card className="space-y-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 border-b border-zinc-800/80 pb-4">
          <div>
            <h2 className="text-sm font-medium text-zinc-200">Interactive Proposed Schedule Change</h2>
            <p className="text-xs text-zinc-400">Select a source task, propose a new planned start date, and evaluate downstream impact</p>
          </div>

          {selectedTask && previewData && previewData.summary.changedTaskCount > 0 && (
            <Button
              size="sm"
              variant="primary"
              onClick={handleCommitChange}
              disabled={committing || loadingPreview}
              className="text-xs"
            >
              {committing ? 'Committing Changes...' : 'Commit Proposed Schedule to DB'}
            </Button>
          )}
        </div>

        {/* Task Selection and Propose Date */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 items-end bg-zinc-950/60 p-4 rounded-lg border border-zinc-800/80">
          <div className="space-y-1.5">
            <label htmlFor="source-task-select" className="block text-xs font-mono uppercase text-zinc-400">
              Source Task to Reschedule:
            </label>
            <select
              id="source-task-select"
              value={selectedTaskId}
              onChange={(e) => handleSelectTask(e.target.value)}
              className="w-full bg-zinc-900 border border-zinc-700/80 rounded-md px-3 py-2 text-xs text-zinc-100 focus:outline-none focus:border-emerald-500"
            >
              {tasks.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.title} ({t.durationDays}d, planned: {t.plannedStartDate || t.startDate})
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <label htmlFor="proposed-date-input" className="block text-xs font-mono uppercase text-zinc-400">
              Proposed Planned Start Date:
            </label>
            <input
              id="proposed-date-input"
              type="date"
              value={proposedDate}
              onChange={(e) => handleDateChange(e.target.value)}
              className="w-full bg-zinc-900 border border-zinc-700/80 rounded-md px-3 py-1.5 text-xs text-zinc-100 focus:outline-none focus:border-emerald-500"
            />
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={handleRunPreview}
              disabled={loadingPreview || !selectedTaskId}
              className="w-full text-xs h-9"
            >
              {loadingPreview ? 'Calculating...' : 'Recalculate Impact'}
            </Button>
          </div>
        </div>

        {/* Quick Shift Adjustments */}
        <div className="flex flex-wrap items-center gap-2 text-xs">
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
            onClick={() => handleShiftDays(5)}
            className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-zinc-300 hover:bg-zinc-800 text-xs"
          >
            +5 Days
          </button>
          <button
            onClick={() => handleShiftDays(-2)}
            className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-zinc-300 hover:bg-zinc-800 text-xs"
          >
            -2 Days
          </button>
          {selectedTask && (
            <button
              onClick={() => setProposedDate(selectedTask.plannedStartDate || selectedTask.startDate || '')}
              className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-zinc-400 hover:text-zinc-200 text-xs ml-auto"
            >
              Reset to Current
            </button>
          )}
        </div>
      </Card>

      {/* Simulation Results Section */}
      {previewData && (
        <Card className="space-y-6">
          <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
            <div>
              <h2 className="text-sm font-semibold text-zinc-100">
                Authoritative Backend Impact Simulation
              </h2>
              <p className="text-xs text-zinc-400">
                Calculated strictly in-memory without database side-effects
              </p>
            </div>
            <Badge variant={previewData.summary.changedTaskCount > 0 ? 'warning' : 'ready'}>
              {previewData.summary.changedTaskCount > 0
                ? `${previewData.summary.changedTaskCount} Tasks Delayed`
                : 'No Downstream Shift'}
            </Badge>
          </div>

          {/* Impact Summary Metrics */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="p-3 rounded-lg bg-zinc-950/60 border border-zinc-800/80 space-y-1">
              <span className="text-[11px] font-mono uppercase text-zinc-500">Affected Subgraph</span>
              <div className="text-xl font-bold font-mono text-zinc-100">
                {previewData.summary.affectedTaskCount}
              </div>
              <p className="text-[10px] text-zinc-500">Downstream tasks evaluated</p>
            </div>

            <div className="p-3 rounded-lg bg-zinc-950/60 border border-zinc-800/80 space-y-1">
              <span className="text-[11px] font-mono uppercase text-zinc-500">Changed Tasks</span>
              <div className={`text-xl font-bold font-mono ${previewData.summary.changedTaskCount > 0 ? 'text-amber-400' : 'text-zinc-300'}`}>
                {previewData.summary.changedTaskCount}
              </div>
              <p className="text-[10px] text-zinc-500">Tasks with altered dates</p>
            </div>

            <div className="p-3 rounded-lg bg-zinc-950/60 border border-zinc-800/80 space-y-1">
              <span className="text-[11px] font-mono uppercase text-zinc-500">Unchanged Tasks</span>
              <div className="text-xl font-bold font-mono text-zinc-300">
                {previewData.summary.unchangedTaskCount}
              </div>
              <p className="text-[10px] text-zinc-500">Unaffected by proposed shift</p>
            </div>

            <div className="p-3 rounded-lg bg-zinc-950/60 border border-zinc-800/80 space-y-1">
              <span className="text-[11px] font-mono uppercase text-zinc-500">Max Delay</span>
              <div className={`text-xl font-bold font-mono ${previewData.summary.maximumDelayDays > 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                +{previewData.summary.maximumDelayDays}d
              </div>
              <p className="text-[10px] text-zinc-500">Maximum propagation slip</p>
            </div>
          </div>

          {/* Impacted Tasks Breakdown Table */}
          <div className="overflow-x-auto rounded border border-zinc-800/80">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-950 text-zinc-400 font-mono text-[11px] border-b border-zinc-800 uppercase">
                <tr>
                  <th className="py-2.5 px-3">Task Title</th>
                  <th className="py-2.5 px-3">Current Scheduled</th>
                  <th className="py-2.5 px-3">Proposed Scheduled</th>
                  <th className="py-2.5 px-3">Shift</th>
                  <th className="py-2.5 px-3">Impact Type</th>
                  <th className="py-2.5 px-3">Causal Engine Explanation</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60 bg-zinc-900/30">
                {previewData.tasks.map((pt) => (
                  <tr key={pt.taskId} className="hover:bg-zinc-800/30 transition-colors">
                    <td className="py-2.5 px-3 max-w-xs">
                      <div className="font-medium text-zinc-200 truncate">{pt.title}</div>
                      <div className="text-[10px] font-mono text-zinc-500">{pt.durationDays}d duration</div>
                    </td>
                    <td className="py-2.5 px-3 font-mono text-zinc-400">
                      {formatDateRange(pt.currentScheduledStart, pt.currentScheduledDue)}
                    </td>
                    <td className="py-2.5 px-3 font-mono text-zinc-200">
                      <span className={pt.shiftDays > 0 ? 'text-amber-400 font-semibold' : ''}>
                        {formatDateRange(pt.proposedScheduledStart, pt.proposedScheduledDue)}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 font-mono font-bold">
                      {pt.shiftDays === 0 ? (
                        <span className="text-zinc-500">0d</span>
                      ) : pt.shiftDays > 0 ? (
                        <span className="text-amber-400">+{pt.shiftDays}d</span>
                      ) : (
                        <span className="text-emerald-400">{pt.shiftDays}d</span>
                      )}
                    </td>
                    <td className="py-2.5 px-3">
                      <Badge
                        variant={
                          pt.impactType === 'DELAYED'
                            ? 'warning'
                            : pt.impactType === 'UNCHANGED'
                            ? 'neutral'
                            : 'ready'
                        }
                      >
                        {pt.impactType}
                      </Badge>
                    </td>
                    <td className="py-2.5 px-3 text-zinc-400 max-w-sm text-[11px] leading-relaxed">
                      {pt.reason}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
