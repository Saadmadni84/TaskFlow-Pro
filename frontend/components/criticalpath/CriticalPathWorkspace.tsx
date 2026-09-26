'use client';

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import Link from 'next/link';
import { useProjects } from '@/hooks/useProjects';
import { criticalPathApi, ApiClientError } from '@/lib/api';
import { CriticalPathResponse, TaskMetrics } from '@/types';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { formatCalendarDate } from '@/lib/utils/dates';

type FilterType = 'ALL' | 'CRITICAL' | 'FLOAT';

export const CriticalPathWorkspace: React.FC = () => {
  // 1. Project Management
  const {
    projects,
    selectedProjectId,
    setSelectedProjectId,
  } = useProjects();

  // 2. Critical Path Data State
  const [data, setData] = useState<CriticalPathResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // 3. UI Filtering & Selection State
  const [filterType, setFilterType] = useState<FilterType>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  // Fetch Critical Path Analysis from authoritative backend
  const fetchAnalysis = useCallback(async () => {
    if (!selectedProjectId) {
      setData(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await criticalPathApi.getCriticalPath(selectedProjectId);
      setData(response);
      // Auto-select first critical task if available, or first task
      if (response.tasks.length > 0) {
        const firstCritical = response.tasks.find((t) => t.isCritical);
        setSelectedTaskId(firstCritical ? firstCritical.taskId : response.tasks[0].taskId);
      } else {
        setSelectedTaskId(null);
      }
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        setError(err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Unable to calculate the critical path. Please try again.');
      }
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [selectedProjectId]);

  useEffect(() => {
    fetchAnalysis();
  }, [fetchAnalysis]);

  // Lookup map: taskId -> TaskMetrics
  const taskMap = useMemo(() => {
    const map = new Map<string, TaskMetrics>();
    if (data?.tasks) {
      for (const t of data.tasks) {
        map.set(t.taskId, t);
      }
    }
    return map;
  }, [data]);

  // Selected task metrics
  const selectedTask = useMemo(() => {
    if (!selectedTaskId || !taskMap.has(selectedTaskId)) return null;
    return taskMap.get(selectedTaskId) || null;
  }, [selectedTaskId, taskMap]);

  // Filtered task list
  const filteredTasks = useMemo(() => {
    if (!data?.tasks) return [];
    return data.tasks.filter((task) => {
      // Status filter
      if (filterType === 'CRITICAL' && !task.isCritical) return false;
      if (filterType === 'FLOAT' && task.isCritical) return false;

      // Search query filter
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        return task.title.toLowerCase().includes(query);
      }

      return true;
    });
  }, [data, filterType, searchQuery]);

  // Has dependencies check (if criticalPaths is empty and all tasks have slack or duration equals project duration)
  const isAllIndependent = useMemo(() => {
    if (!data || data.tasks.length <= 1) return false;
    return data.criticalPaths.length === 0;
  }, [data]);

  return (
    <div className="max-w-7xl mx-auto space-y-8 pb-12">
      {/* Top Header & Breadcrumb */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 border-b border-zinc-800/80">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-mono text-amber-400 uppercase tracking-widest bg-amber-950/60 border border-amber-500/30 px-2 py-0.5 rounded">
              Phase 8 Architecture
            </span>
            <span className="text-zinc-600">/</span>
            <span className="text-xs font-mono text-zinc-400">Critical Path Method (CPM)</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">
            Critical Path Analysis
          </h1>
          <p className="text-xs text-zinc-400 leading-relaxed max-w-3xl">
            Authoritative, deterministic Critical Path Method (CPM) calculated via Kahn&apos;s topological sort and inclusive calendar date passes.
            Identifies zero-float bottlenecks that dictate project completion.
          </p>
        </div>

        {/* Project Switcher & Toolbar Actions */}
        <div className="flex flex-wrap items-center gap-2">
          {projects.length > 0 && (
            <select
              value={selectedProjectId || ''}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              className="px-3 py-1.5 text-xs rounded-md bg-zinc-900 border border-zinc-800 text-zinc-200 focus:outline-none focus:ring-1 focus:ring-amber-500"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          )}

          <Button
            variant="outline"
            size="sm"
            onClick={fetchAnalysis}
            disabled={loading || !selectedProjectId}
            className="text-xs flex items-center gap-1.5 border-zinc-700 hover:bg-zinc-800"
            title="Recalculate critical path and boundary timings"
          >
            <svg
              className={`w-3.5 h-3.5 text-zinc-300 ${loading ? 'animate-spin' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            <span>{loading ? 'Calculating...' : 'Recalculate'}</span>
          </Button>

          {selectedProjectId && (
            <Link href={`/graph?critical=true`}>
              <Button
                variant="secondary"
                size="sm"
                className="text-xs flex items-center gap-1.5 border-amber-500/30 text-amber-300 hover:bg-amber-950/40"
              >
                <span>View in Visual DAG</span>
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </Button>
            </Link>
          )}
        </div>
      </div>

      {/* Loading State */}
      {loading && (
        <div className="py-24 flex flex-col items-center justify-center space-y-3 text-center border border-zinc-800/80 rounded-2xl bg-zinc-950/60">
          <div className="h-8 w-8 border-2 border-zinc-700 border-t-amber-400 rounded-full animate-spin" />
          <div className="text-sm font-semibold text-zinc-200">Calculating critical path...</div>
          <p className="text-xs text-zinc-500 font-mono">
            Evaluating topological DAG constraints, forward/backward passes, and float distribution.
          </p>
        </div>
      )}

      {/* Error State */}
      {!loading && error && (
        <div className="p-6 rounded-2xl border border-rose-800/60 bg-rose-950/20 text-center space-y-3">
          <div className="w-10 h-10 mx-auto rounded-full bg-rose-900/40 border border-rose-700/50 flex items-center justify-center text-rose-400">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-zinc-100">Unable to calculate the critical path.</h3>
          <p className="text-xs text-rose-300 max-w-md mx-auto">{error}</p>
          <Button variant="secondary" size="sm" onClick={fetchAnalysis}>
            Try Again
          </Button>
        </div>
      )}

      {/* Empty Project State */}
      {!loading && !error && (!data || data.tasks.length === 0) && (
        <div className="py-20 flex flex-col items-center justify-center space-y-3 text-center border border-dashed border-zinc-800 rounded-2xl bg-zinc-950/40 p-8">
          <div className="w-12 h-12 rounded-full bg-zinc-900 border border-zinc-800 flex items-center justify-center text-zinc-400">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-zinc-200">No tasks available for Critical Path Analysis.</h3>
          <p className="text-xs text-zinc-400 max-w-sm">
            Create tasks and connect dependencies in this project to calculate the project&apos;s critical path.
          </p>
          <Link href="/kanban" className="pt-2">
            <Button variant="primary" size="sm">
              Go to Kanban Workspace
            </Button>
          </Link>
        </div>
      )}

      {/* Calculated Results Dashboard */}
      {!loading && !error && data && data.tasks.length > 0 && (
        <div className="space-y-6">
          {/* All Independent Tasks Notice */}
          {isAllIndependent && (
            <div className="p-3.5 rounded-xl border border-sky-800/50 bg-sky-950/20 text-xs text-sky-200 flex items-start gap-2.5">
              <svg className="w-4 h-4 text-sky-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div>
                <strong className="text-sky-100 font-semibold">Independent Tasks: </strong>
                No dependency chain exists yet. All tasks are currently independent. The project completion date is determined by the latest task finish.
              </div>
            </div>
          )}

          {/* Section 23: Project Summary Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* 1. Project Duration */}
            <Card className="p-4 space-y-1 bg-zinc-900/60 border-zinc-800/80">
              <span className="text-[11px] font-mono uppercase tracking-wider text-zinc-500">
                Project Duration
              </span>
              <div className="text-2xl font-bold font-mono text-zinc-100">
                {data.projectDurationDays ??
                  (data.projectStartDate && data.projectCompletionDate
                    ? 'Calculated'
                    : '—')}{' '}
                <span className="text-xs font-normal text-zinc-400">days</span>
              </div>
              <div className="text-[11px] font-mono text-zinc-500 pt-1 border-t border-zinc-800/60">
                Start: {formatCalendarDate(data.projectStartDate)}
              </div>
            </Card>

            {/* 2. Project Finish */}
            <Card className="p-4 space-y-1 bg-zinc-900/60 border-zinc-800/80">
              <span className="text-[11px] font-mono uppercase tracking-wider text-zinc-500">
                Project Finish
              </span>
              <div className="text-xl font-bold font-mono text-emerald-400">
                {formatCalendarDate(data.projectCompletionDate)}
              </div>
              <div className="text-[11px] font-mono text-zinc-500 pt-1 border-t border-zinc-800/60">
                Earliest completion milestone
              </div>
            </Card>

            {/* 3. Critical Tasks */}
            <Card className="p-4 space-y-1 bg-zinc-900/60 border-zinc-800/80">
              <span className="text-[11px] font-mono uppercase tracking-wider text-zinc-500">
                Critical Tasks
              </span>
              <div className="text-2xl font-bold font-mono text-amber-400 flex items-center gap-2">
                <span>{data.criticalTaskIds.length}</span>
                <span className="text-xs font-normal text-amber-400/80 font-mono">
                  ({Math.round((data.criticalTaskIds.length / data.tasks.length) * 100)}%)
                </span>
              </div>
              <div className="text-[11px] font-mono text-zinc-500 pt-1 border-t border-zinc-800/60">
                Zero Total Float (no slack)
              </div>
            </Card>

            {/* 4. Critical Paths */}
            <Card className="p-4 space-y-1 bg-zinc-900/60 border-zinc-800/80">
              <span className="text-[11px] font-mono uppercase tracking-wider text-zinc-500">
                Critical Paths
              </span>
              <div className="text-2xl font-bold font-mono text-sky-400">
                {data.criticalPaths.length}
                <span className="text-xs font-normal text-zinc-400 ml-1">
                  parallel {data.criticalPaths.length === 1 ? 'path' : 'paths'}
                </span>
              </div>
              <div className="text-[11px] font-mono text-zinc-500 pt-1 border-t border-zinc-800/60">
                Deterministic CPM chains
              </div>
            </Card>
          </div>

          {/* Section 26: Critical Paths Section */}
          <Card className="p-5 space-y-4 bg-zinc-900/50 border-zinc-800/80">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-zinc-800/80 pb-3">
              <div>
                <h2 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
                  <span>Critical Dependency Chains</span>
                  <Badge variant="warning">{data.criticalPaths.length} Active</Badge>
                </h2>
                <p className="text-xs text-zinc-400">
                  Tasks along these chains directly determine earliest project delivery. Click any task to inspect its timing.
                </p>
              </div>
              <Link href="/graph?critical=true">
                <Button variant="ghost" size="sm" className="text-xs text-amber-400 hover:text-amber-300">
                  Highlight in Visual DAG →
                </Button>
              </Link>
            </div>

            {data.criticalPaths.length === 0 ? (
              <div className="p-4 rounded-xl bg-zinc-950/70 border border-zinc-800 text-xs text-zinc-500 font-mono text-center">
                No dependency chains detected. All tasks are independent.
              </div>
            ) : (
              <div className="space-y-3">
                {data.criticalPaths.map((path, pathIdx) => (
                  <div
                    key={pathIdx}
                    className="p-3.5 rounded-xl bg-zinc-950/80 border border-amber-500/30 space-y-2.5 shadow-sm"
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-[11px] font-mono uppercase text-amber-400 font-semibold tracking-wide flex items-center gap-1.5">
                        <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
                        Critical Path {pathIdx + 1} ({path.length} tasks)
                      </span>
                      <Link
                        href={`/graph?critical=true&taskId=${path[0]}`}
                        className="text-[11px] font-mono text-zinc-400 hover:text-amber-300 transition-colors"
                      >
                        Inspect in DAG ↗
                      </Link>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 text-xs">
                      {path.map((taskId, nodeIdx) => {
                        const taskMetrics = taskMap.get(taskId);
                        const isSelected = selectedTaskId === taskId;
                        return (
                          <React.Fragment key={taskId}>
                            <button
                              type="button"
                              onClick={() => setSelectedTaskId(taskId)}
                              className={`px-3 py-1.5 rounded-lg border text-xs font-medium transition-all ${
                                isSelected
                                  ? 'bg-amber-950/90 border-amber-400 text-amber-200 ring-2 ring-amber-400/40 shadow-sm'
                                  : 'bg-zinc-900 border-amber-500/30 text-zinc-200 hover:border-amber-400 hover:text-amber-100'
                              }`}
                              title={`Click to inspect ${taskMetrics?.title || taskId}`}
                            >
                              <span className="font-semibold">{taskMetrics?.title || taskId}</span>
                              {taskMetrics && (
                                <span className="ml-1.5 font-mono text-[10px] text-zinc-400">
                                  ({taskMetrics.durationDays}d)
                                </span>
                              )}
                            </button>
                            {nodeIdx < path.length - 1 && (
                              <span className="text-amber-400 font-bold px-0.5 select-none">→</span>
                            )}
                          </React.Fragment>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Section 28 & 29: Selected Task Timing Callout */}
          {selectedTask && (
            <Card className="p-5 bg-zinc-900/70 border-zinc-800 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-zinc-800 pb-3">
                <div className="flex items-center gap-2.5">
                  <span
                    className={`w-3 h-3 rounded-full shrink-0 ${
                      selectedTask.isCritical ? 'bg-amber-400 animate-pulse' : 'bg-emerald-400'
                    }`}
                  />
                  <div>
                    <h3 className="text-sm font-semibold text-zinc-100">{selectedTask.title}</h3>
                    <p className="text-[11px] font-mono text-zinc-400">
                      Duration: {selectedTask.durationDays} calendar {selectedTask.durationDays === 1 ? 'day' : 'days'}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Badge variant={selectedTask.isCritical ? 'warning' : 'ready'}>
                    {selectedTask.isCritical ? 'CRITICAL PATH' : 'FLOAT AVAILABLE'}
                  </Badge>
                  <span className="font-mono text-xs text-zinc-400 px-2 py-0.5 rounded bg-zinc-950 border border-zinc-800">
                    Total Float: {selectedTask.totalSlackDays} {selectedTask.totalSlackDays === 1 ? 'day' : 'days'}
                  </span>
                </div>
              </div>

              {/* Explanatory Statement per Section 28 & 29 */}
              <div
                className={`p-3 rounded-xl border text-xs leading-relaxed ${
                  selectedTask.isCritical
                    ? 'bg-amber-950/30 border-amber-500/30 text-amber-200'
                    : 'bg-zinc-950/80 border-zinc-800 text-zinc-300'
                }`}
              >
                {selectedTask.isCritical ? (
                  <p>
                    <strong className="text-amber-300">Critical Status: </strong>
                    This task has <code className="font-mono font-bold text-amber-300">0 days</code> of total float.
                    Any scheduling delay in this task will directly delay the project delivery date (
                    {formatCalendarDate(data.projectCompletionDate)}).
                  </p>
                ) : (
                  <p>
                    <strong className="text-emerald-400">Scheduling Flexibility: </strong>
                    This task can move by up to{' '}
                    <code className="font-mono font-bold text-emerald-300">
                      {selectedTask.totalSlackDays} {selectedTask.totalSlackDays === 1 ? 'day' : 'days'}
                    </code>{' '}
                    without changing the current project completion date, assuming other constraints remain unchanged.
                  </p>
                )}
              </div>

              {/* Precise Boundary Timing Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center text-xs">
                <div className="p-2.5 rounded-lg bg-zinc-950 border border-zinc-800/80">
                  <div className="text-[10px] font-mono text-zinc-500 uppercase">Earliest Start (ES)</div>
                  <div className="text-xs font-mono font-semibold text-zinc-200 mt-1">
                    {formatCalendarDate(selectedTask.earliestStart)}
                  </div>
                </div>

                <div className="p-2.5 rounded-lg bg-zinc-950 border border-zinc-800/80">
                  <div className="text-[10px] font-mono text-zinc-500 uppercase">Earliest Finish (EF)</div>
                  <div className="text-xs font-mono font-semibold text-zinc-200 mt-1">
                    {formatCalendarDate(selectedTask.earliestFinish)}
                  </div>
                </div>

                <div className="p-2.5 rounded-lg bg-zinc-950 border border-zinc-800/80">
                  <div className="text-[10px] font-mono text-zinc-500 uppercase">Latest Start (LS)</div>
                  <div className="text-xs font-mono font-semibold text-zinc-200 mt-1">
                    {formatCalendarDate(selectedTask.latestStart)}
                  </div>
                </div>

                <div className="p-2.5 rounded-lg bg-zinc-950 border border-zinc-800/80">
                  <div className="text-[10px] font-mono text-zinc-500 uppercase">Latest Finish (LF)</div>
                  <div className="text-xs font-mono font-semibold text-zinc-200 mt-1">
                    {formatCalendarDate(selectedTask.latestFinish)}
                  </div>
                </div>
              </div>

              {/* Invariant equation verification */}
              <div className="text-[11px] font-mono text-zinc-500 flex items-center justify-between pt-1">
                <span>
                  Float Invariant: <code className="text-zinc-400">LS - ES = LF - EF = {selectedTask.totalSlackDays}d</code>
                </span>
                <Link
                  href={`/graph?critical=true&taskId=${selectedTask.taskId}`}
                  className="text-amber-400 hover:text-amber-300 underline"
                >
                  Locate in Visual DAG
                </Link>
              </div>
            </Card>
          )}

          {/* Section 24 & 25: Task Analysis Table */}
          <Card className="p-5 space-y-4 bg-zinc-900/50 border-zinc-800/80">
            {/* Table Controls: Search & Filters */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-zinc-100">Task Schedule & Slack Breakdown</h2>
                <p className="text-xs text-zinc-400">
                  Forward/Backward pass boundary timings and total float for all project tasks.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                {/* Search input */}
                <input
                  type="text"
                  placeholder="Filter tasks..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="px-2.5 py-1 text-xs rounded-md bg-zinc-950 border border-zinc-800 text-zinc-200 focus:outline-none focus:ring-1 focus:ring-amber-500 placeholder-zinc-600 w-36 sm:w-44"
                />

                {/* Filter pills */}
                <div className="flex rounded-lg bg-zinc-950 p-0.5 border border-zinc-800 text-[11px] font-mono">
                  <button
                    onClick={() => setFilterType('ALL')}
                    className={`px-2 py-1 rounded transition-colors ${
                      filterType === 'ALL'
                        ? 'bg-zinc-800 text-zinc-100 font-semibold'
                        : 'text-zinc-500 hover:text-zinc-300'
                    }`}
                  >
                    All ({data.tasks.length})
                  </button>
                  <button
                    onClick={() => setFilterType('CRITICAL')}
                    className={`px-2 py-1 rounded transition-colors ${
                      filterType === 'CRITICAL'
                        ? 'bg-amber-950/80 text-amber-300 font-semibold border border-amber-500/30'
                        : 'text-zinc-500 hover:text-zinc-300'
                    }`}
                  >
                    Critical ({data.criticalTaskIds.length})
                  </button>
                  <button
                    onClick={() => setFilterType('FLOAT')}
                    className={`px-2 py-1 rounded transition-colors ${
                      filterType === 'FLOAT'
                        ? 'bg-zinc-800 text-emerald-400 font-semibold'
                        : 'text-zinc-500 hover:text-zinc-300'
                    }`}
                  >
                    Float ({data.tasks.length - data.criticalTaskIds.length})
                  </button>
                </div>
              </div>
            </div>

            {/* Horizontally scrollable table */}
            <div className="overflow-x-auto rounded-xl border border-zinc-800/80">
              <table className="w-full text-left text-xs">
                <thead className="bg-zinc-950 text-zinc-400 font-mono text-[11px] uppercase tracking-wider border-b border-zinc-800/80">
                  <tr>
                    <th scope="col" className="py-3 px-4">Task</th>
                    <th scope="col" className="py-3 px-3 text-center">Duration</th>
                    <th scope="col" className="py-3 px-3">Earliest Start (ES)</th>
                    <th scope="col" className="py-3 px-3">Earliest Finish (EF)</th>
                    <th scope="col" className="py-3 px-3">Latest Start (LS)</th>
                    <th scope="col" className="py-3 px-3">Latest Finish (LF)</th>
                    <th scope="col" className="py-3 px-3 text-right">Total Float</th>
                    <th scope="col" className="py-3 px-4 text-center">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-800/60 font-mono">
                  {filteredTasks.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="py-8 text-center text-xs text-zinc-500">
                        No tasks match the selected filter.
                      </td>
                    </tr>
                  ) : (
                    filteredTasks.map((task) => {
                      const isSelected = selectedTaskId === task.taskId;
                      return (
                        <tr
                          key={task.taskId}
                          onClick={() => setSelectedTaskId(task.taskId)}
                          className={`cursor-pointer transition-colors ${
                            isSelected
                              ? 'bg-amber-950/30 text-zinc-100 font-semibold'
                              : 'hover:bg-zinc-900/60 text-zinc-300'
                          }`}
                        >
                          <td className="py-3 px-4 font-sans font-medium flex items-center gap-2">
                            <span
                              className={`w-2 h-2 rounded-full shrink-0 ${
                                task.isCritical ? 'bg-amber-400' : 'bg-emerald-400'
                              }`}
                            />
                            <span className="truncate max-w-[220px]" title={task.title}>
                              {task.title}
                            </span>
                          </td>
                          <td className="py-3 px-3 text-center text-zinc-400">
                            {task.durationDays}d
                          </td>
                          <td className="py-3 px-3 text-zinc-300">
                            {formatCalendarDate(task.earliestStart)}
                          </td>
                          <td className="py-3 px-3 text-zinc-300">
                            {formatCalendarDate(task.earliestFinish)}
                          </td>
                          <td className="py-3 px-3 text-zinc-400">
                            {formatCalendarDate(task.latestStart)}
                          </td>
                          <td className="py-3 px-3 text-zinc-400">
                            {formatCalendarDate(task.latestFinish)}
                          </td>
                          <td className="py-3 px-3 text-right">
                            <span
                              className={`px-2 py-0.5 rounded text-[11px] ${
                                task.isCritical
                                  ? 'bg-amber-950/80 text-amber-300 border border-amber-500/30'
                                  : 'bg-zinc-800 text-zinc-300'
                              }`}
                            >
                              {task.totalSlackDays}d
                            </span>
                          </td>
                          <td className="py-3 px-4 text-center">
                            {task.isCritical ? (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded font-mono text-[10px] font-semibold bg-amber-950/90 text-amber-300 border border-amber-500/40">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
                                CRITICAL
                              </span>
                            ) : (
                              <span className="inline-flex items-center px-2 py-0.5 rounded font-mono text-[10px] bg-zinc-800/80 text-zinc-400 border border-zinc-700/40">
                                FLOAT
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </Card>

          {/* Section 55 & 56: Secondary Explanatory Cards */}
          <div className="pt-4 border-t border-zinc-800/80 space-y-3">
            <div className="text-[11px] font-mono uppercase text-zinc-500 tracking-wider">
              How Critical Path Method (CPM) Operates
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Card className="p-4 space-y-2 bg-zinc-950/40 border-zinc-800/80">
                <Badge variant="neutral">Algorithm</Badge>
                <div className="text-xs font-semibold text-zinc-200">Forward & Backward Pass</div>
                <p className="text-[11px] text-zinc-400 leading-relaxed">
                  The forward pass propagates in topological order to determine Earliest Start (ES) and Earliest Finish (EF).
                  The backward pass propagates in reverse topological order from project completion to determine Latest Finish (LF) and Latest Start (LS).
                </p>
              </Card>

              <Card className="p-4 space-y-2 bg-zinc-950/40 border-zinc-800/80">
                <Badge variant="neutral">Metric</Badge>
                <div className="text-xs font-semibold text-zinc-200">Zero Total Float (Slack)</div>
                <p className="text-[11px] text-zinc-400 leading-relaxed">
                  Total Float is calculated as <code className="text-zinc-300 font-mono">LS - ES = LF - EF</code>.
                  Tasks with zero total float reside on the critical path; delaying them immediately pushes back project completion.
                </p>
              </Card>

              <Card className="p-4 space-y-2 bg-zinc-950/40 border-zinc-800/80">
                <Badge variant="neutral">Visualization</Badge>
                <div className="text-xs font-semibold text-zinc-200">Visual DAG Critical Chain</div>
                <p className="text-[11px] text-zinc-400 leading-relaxed">
                  The Visual DAG graph emphasizes critical tasks and critical edges with distinct amber borders and animated links,
                  while non-critical tasks remain visible with secondary styling.
                </p>
              </Card>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
