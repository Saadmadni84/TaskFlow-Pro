'use client';

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { projectApi, taskApi, dependencyApi } from '@/lib/api';
import { Project, Task, TaskDependency } from '@/types';

export const DomainModelWorkspace: React.FC = () => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [tasks, setTasks] = useState<Task[]>([]);
  const [dependencies, setDependencies] = useState<TaskDependency[]>([]);
  const [loadingProjects, setLoadingProjects] = useState<boolean>(true);
  const [loadingData, setLoadingData] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'tasks' | 'dependencies' | 'projects'>('tasks');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Fetch all projects initially
  const loadProjects = useCallback(async () => {
    try {
      setLoadingProjects(true);
      setError(null);
      const data = await projectApi.getProjects();
      setProjects(data);
      if (data.length > 0) {
        setSelectedProjectId((prev) => prev || data[0].id);
      }
    } catch (err: unknown) {
      console.error('Failed to load projects', err);
      setError('Failed to connect to PostgreSQL backend. Ensure the backend daemon is running.');
    } finally {
      setLoadingProjects(false);
    }
  }, []);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  // Fetch tasks and dependencies whenever selected project changes
  const loadProjectData = useCallback(async (projectId: string) => {
    if (!projectId) return;
    try {
      setLoadingData(true);
      setError(null);
      const [fetchedTasks, fetchedDeps] = await Promise.all([
        taskApi.getTasksByProject(projectId),
        dependencyApi.getDependenciesByProject(projectId),
      ]);
      setTasks(fetchedTasks);
      setDependencies(fetchedDeps);
    } catch (err: unknown) {
      console.error('Failed to load domain entities for project', err);
      setError('Failed to load domain records from PostgreSQL.');
    } finally {
      setLoadingData(false);
    }
  }, []);

  useEffect(() => {
    if (selectedProjectId) {
      loadProjectData(selectedProjectId);
    }
  }, [selectedProjectId, loadProjectData]);

  const handleCopy = (id: string) => {
    navigator.clipboard.writeText(id);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1800);
  };

  const selectedProject = useMemo(() => {
    return projects.find((p) => p.id === selectedProjectId) || null;
  }, [projects, selectedProjectId]);

  const taskMap = useMemo(() => {
    const map = new Map<string, Task>();
    tasks.forEach((t) => map.set(t.id, t));
    return map;
  }, [tasks]);

  // Statistics
  const stats = useMemo(() => {
    const totalTasks = tasks.length;
    const readyTasks = tasks.filter((t) => t.dependencyStatus === 'READY').length;
    const blockedTasks = tasks.filter((t) => t.dependencyStatus === 'BLOCKED').length;
    const doneTasks = tasks.filter((t) => t.workflowStatus === 'DONE').length;
    const inProgressTasks = tasks.filter((t) => t.workflowStatus === 'IN_PROGRESS').length;
    const backlogTasks = tasks.filter((t) => t.workflowStatus === 'BACKLOG').length;
    const totalEdges = dependencies.length;
    const maxVersion = tasks.reduce((max, t) => Math.max(max, t.version ?? 0), 0);

    return {
      totalTasks,
      readyTasks,
      blockedTasks,
      doneTasks,
      inProgressTasks,
      backlogTasks,
      totalEdges,
      maxVersion,
    };
  }, [tasks, dependencies]);

  // Filtered tasks
  const filteredTasks = useMemo(() => {
    return tasks.filter((t) => {
      const matchesSearch =
        t.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        t.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (t.description && t.description.toLowerCase().includes(searchQuery.toLowerCase()));

      const matchesStatus =
        statusFilter === 'ALL' ||
        t.workflowStatus === statusFilter ||
        t.dependencyStatus === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [tasks, searchQuery, statusFilter]);

  // Filtered dependencies
  const filteredDependencies = useMemo(() => {
    return dependencies.filter((dep) => {
      const pred = taskMap.get(dep.predecessorTaskId);
      const succ = taskMap.get(dep.successorTaskId);
      const q = searchQuery.toLowerCase();

      return (
        dep.id.toLowerCase().includes(q) ||
        dep.predecessorTaskId.toLowerCase().includes(q) ||
        dep.successorTaskId.toLowerCase().includes(q) ||
        (pred && pred.title.toLowerCase().includes(q)) ||
        (succ && succ.title.toLowerCase().includes(q))
      );
    });
  }, [dependencies, taskMap, searchQuery]);

  return (
    <div className="max-w-6xl mx-auto space-y-8 pb-12">
      {/* Overview Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="space-y-1.5">
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
              Phase 2 Domain Model
            </span>
            <span className="text-zinc-600">/</span>
            <span className="text-xs font-mono text-zinc-400">PostgreSQL Persistence & Relational Invariants</span>
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
            Core Domain Model & Persistence Layer
          </h1>
          <p className="text-sm text-zinc-400 max-w-2xl leading-relaxed">
            TaskFlow Pro models projects, tasks, and directed dependencies using JPA/Hibernate backed by PostgreSQL 16.
            All entities enforce database constraints, optimistic locking, and strict project scope isolation.
          </p>
        </div>

        {/* Database & Schema Status Badges */}
        <div className="flex flex-wrap md:flex-col items-start md:items-end gap-2 shrink-0">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-emerald-950/50 border border-emerald-500/30 text-emerald-400 text-xs font-mono">
            <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
            <span>PostgreSQL 16 • Connected</span>
          </div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-md bg-zinc-900 border border-zinc-800 text-zinc-400 text-[11px] font-mono">
            <span>Flyway V1–V5 Migrations Applied</span>
          </div>
        </div>
      </div>

      {/* Project Selector & Actions Bar */}
      <Card className="p-4 bg-zinc-900/80 border-zinc-800 space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <label htmlFor="project-select" className="text-xs font-mono text-zinc-400 uppercase shrink-0">
              Workspace Aggregate:
            </label>
            {loadingProjects ? (
              <Skeleton className="h-8 w-60" />
            ) : (
              <select
                id="project-select"
                value={selectedProjectId}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                className="bg-zinc-950 border border-zinc-700/80 rounded-md px-3 py-1.5 text-xs text-zinc-200 font-medium focus:outline-none focus:border-emerald-500 min-w-[260px]"
              >
                {projects.map((proj) => (
                  <option key={proj.id} value={proj.id}>
                    {proj.name} ({proj.id.substring(0, 8)}...)
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
              disabled={loadingData || !selectedProjectId}
              className="text-xs flex items-center gap-1.5"
            >
              <svg
                className={`w-3.5 h-3.5 ${loadingData ? 'animate-spin' : ''}`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              <span>Refresh Records</span>
            </Button>
          </div>
        </div>

        {selectedProject && (
          <div className="pt-2 border-t border-zinc-800/80 flex flex-wrap items-center justify-between text-[11px] font-mono text-zinc-500 gap-2">
            <div className="flex items-center gap-2">
              <span className="text-zinc-400">UUID PK:</span>
              <code className="text-zinc-300 bg-zinc-950 px-1.5 py-0.5 rounded border border-zinc-800">
                {selectedProject.id}
              </code>
              <button
                onClick={() => handleCopy(selectedProject.id)}
                className="hover:text-zinc-300 transition-colors text-[10px] text-zinc-500 underline"
              >
                {copiedId === selectedProject.id ? 'Copied!' : 'Copy'}
              </button>
            </div>
            <div className="flex items-center gap-3">
              <span className="text-emerald-400 flex items-center gap-1">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                Scope Boundary: Isolated
              </span>
              <span className="text-zinc-500">|</span>
              <span className="text-zinc-400">
                Optimistic Locking: <code className="text-zinc-300">@Version BIGINT</code>
              </span>
            </div>
          </div>
        )}
      </Card>

      {/* Error alert if any */}
      {error && (
        <div className="p-3.5 rounded-lg border border-rose-500/40 bg-rose-950/40 text-rose-300 text-xs flex items-center justify-between">
          <span>{error}</span>
          <Button size="sm" variant="outline" onClick={loadProjects}>Retry</Button>
        </div>
      )}

      {/* Live Domain Metrics Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Project Aggregate */}
        <Card className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Entity</span>
            <Badge variant="ready">Project</Badge>
          </div>
          <div className="text-sm font-semibold text-zinc-200">
            {selectedProject ? selectedProject.name : 'No Project Selected'}
          </div>
          <p className="text-xs text-zinc-400 line-clamp-2">
            {selectedProject?.description || 'Root workspace boundary isolating graphs and tasks.'}
          </p>
          <div className="pt-2 text-[11px] font-mono text-zinc-500 border-t border-zinc-800/60">
            Table: <code className="text-zinc-300">projects</code> (UUID PK)
          </div>
        </Card>

        {/* Persisted Tasks */}
        <Card className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Persisted Tasks</span>
            <Badge variant="neutral">{stats.totalTasks} Rows</Badge>
          </div>
          <div className="text-2xl font-bold font-mono text-zinc-100">
            {stats.totalTasks}
          </div>
          <div className="text-xs text-zinc-400 flex items-center gap-2">
            <span className="text-emerald-400 font-mono">{stats.doneTasks} DONE</span>
            <span>•</span>
            <span className="text-sky-400 font-mono">{stats.inProgressTasks} ACTIVE</span>
            <span>•</span>
            <span className="text-zinc-500 font-mono">{stats.backlogTasks} QUEUED</span>
          </div>
          <div className="pt-2 text-[11px] font-mono text-zinc-500 border-t border-zinc-800/60">
            Table: <code className="text-zinc-300">tasks</code> (Dual-State)
          </div>
        </Card>

        {/* Derived Dependency States */}
        <Card className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Readiness State</span>
            <Badge variant="ready">Derived</Badge>
          </div>
          <div className="flex items-baseline gap-3">
            <span className="text-2xl font-bold font-mono text-emerald-400">{stats.readyTasks}</span>
            <span className="text-xs font-mono text-emerald-500">READY</span>
            <span className="text-zinc-600">/</span>
            <span className="text-2xl font-bold font-mono text-rose-400">{stats.blockedTasks}</span>
            <span className="text-xs font-mono text-rose-500">BLOCKED</span>
          </div>
          <p className="text-xs text-zinc-400">
            Server-derived invariant based on upstream prerequisite task completion.
          </p>
          <div className="pt-2 text-[11px] font-mono text-zinc-500 border-t border-zinc-800/60">
            Column: <code className="text-zinc-300">dependency_status</code>
          </div>
        </Card>

        {/* Directed Graph Edges */}
        <Card className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-zinc-400 uppercase">Directed Edges</span>
            <Badge variant="ready">Dependencies</Badge>
          </div>
          <div className="text-2xl font-bold font-mono text-zinc-100">
            {stats.totalEdges}
          </div>
          <p className="text-xs text-zinc-400">
            Directed constraints (<code className="text-zinc-300">pred → succ</code>) with uniqueness &amp; self-loop defense.
          </p>
          <div className="pt-2 text-[11px] font-mono text-zinc-500 border-t border-zinc-800/60">
            Table: <code className="text-zinc-300">task_dependencies</code>
          </div>
        </Card>
      </div>

      {/* Live Entity Inspector / Database Browser */}
      <Card className="space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-zinc-800/80 pb-3">
          <div>
            <h2 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
              <span>PostgreSQL Live Relational Entity Browser</span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-zinc-800 text-zinc-400 border border-zinc-700/60">
                PostgreSQL 16
              </span>
            </h2>
            <p className="text-xs text-zinc-400">
              Querying live records directly from the Spring Boot JPA persistence layer
            </p>
          </div>

          {/* Entity Tabs */}
          <div className="flex items-center gap-1 bg-zinc-950 p-1 rounded-lg border border-zinc-800 self-start sm:self-auto">
            <button
              onClick={() => setActiveTab('tasks')}
              className={`px-3 py-1 rounded text-xs font-medium transition-all ${
                activeTab === 'tasks'
                  ? 'bg-zinc-800 text-zinc-100 shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Tasks ({tasks.length})
            </button>
            <button
              onClick={() => setActiveTab('dependencies')}
              className={`px-3 py-1 rounded text-xs font-medium transition-all ${
                activeTab === 'dependencies'
                  ? 'bg-zinc-800 text-zinc-100 shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Dependencies ({dependencies.length})
            </button>
            <button
              onClick={() => setActiveTab('projects')}
              className={`px-3 py-1 rounded text-xs font-medium transition-all ${
                activeTab === 'projects'
                  ? 'bg-zinc-800 text-zinc-100 shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Projects ({projects.length})
            </button>
          </div>
        </div>

        {/* Filter / Search Bar */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="w-full sm:w-72">
            <input
              type="text"
              placeholder={`Search ${activeTab}...`}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-zinc-950 border border-zinc-800 rounded px-3 py-1.5 text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-zinc-700"
            />
          </div>

          {activeTab === 'tasks' && (
            <div className="flex items-center gap-2 w-full sm:w-auto overflow-x-auto">
              <span className="text-[11px] font-mono text-zinc-500 uppercase">Filter:</span>
              {['ALL', 'READY', 'BLOCKED', 'DONE', 'IN_PROGRESS', 'BACKLOG'].map((f) => (
                <button
                  key={f}
                  onClick={() => setStatusFilter(f)}
                  className={`px-2 py-0.5 rounded text-[11px] font-mono transition-colors ${
                    statusFilter === f
                      ? 'bg-zinc-700 text-zinc-100 font-semibold'
                      : 'bg-zinc-900 text-zinc-400 hover:text-zinc-200'
                  }`}
                >
                  {f}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Tab 1: Tasks Table */}
        {activeTab === 'tasks' && (
          <div className="overflow-x-auto rounded border border-zinc-800/80">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-950 text-zinc-400 font-mono text-[11px] border-b border-zinc-800 uppercase">
                <tr>
                  <th className="py-2.5 px-3">Task Details</th>
                  <th className="py-2.5 px-3">Workflow State</th>
                  <th className="py-2.5 px-3">Dependency State</th>
                  <th className="py-2.5 px-3">Duration &amp; Schedule</th>
                  <th className="py-2.5 px-3">JPA @Version</th>
                  <th className="py-2.5 px-3 text-right">UUID PK</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60 bg-zinc-900/30">
                {loadingData ? (
                  Array.from({ length: 4 }).map((_, idx) => (
                    <tr key={idx}>
                      <td colSpan={6} className="p-3">
                        <Skeleton className="h-6 w-full" />
                      </td>
                    </tr>
                  ))
                ) : filteredTasks.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-zinc-500">
                      No tasks matching criteria found.
                    </td>
                  </tr>
                ) : (
                  filteredTasks.map((t) => (
                    <tr key={t.id} className="hover:bg-zinc-800/30 transition-colors">
                      <td className="py-2.5 px-3 max-w-xs">
                        <div className="font-medium text-zinc-200 truncate">{t.title}</div>
                        {t.description && (
                          <div className="text-[11px] text-zinc-500 truncate">{t.description}</div>
                        )}
                      </td>
                      <td className="py-2.5 px-3">
                        <Badge
                          variant={
                            t.workflowStatus === 'DONE'
                              ? 'ready'
                              : t.workflowStatus === 'IN_PROGRESS'
                              ? 'warning'
                              : 'neutral'
                          }
                        >
                          {t.workflowStatus}
                        </Badge>
                      </td>
                      <td className="py-2.5 px-3">
                        <Badge variant={t.dependencyStatus === 'READY' ? 'ready' : 'blocked'}>
                          {t.dependencyStatus}
                        </Badge>
                      </td>
                      <td className="py-2.5 px-3 font-mono text-[11px] text-zinc-400">
                        <div>{t.durationDays}d duration</div>
                        {t.scheduledStartDate && t.scheduledDueDate && (
                          <div className="text-zinc-500 text-[10px]">
                            {t.scheduledStartDate} → {t.scheduledDueDate}
                          </div>
                        )}
                      </td>
                      <td className="py-2.5 px-3 font-mono text-[11px]">
                        <span
                          className="px-2 py-0.5 rounded bg-zinc-950 border border-zinc-800 text-zinc-300"
                          title="JPA Optimistic Locking Version Token"
                        >
                          v{t.version ?? 0}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono text-[11px]">
                        <div className="flex items-center justify-end gap-1.5">
                          <code className="text-zinc-400 bg-zinc-950 px-1.5 py-0.5 rounded border border-zinc-800">
                            {t.id.substring(0, 8)}...
                          </code>
                          <button
                            onClick={() => handleCopy(t.id)}
                            className="text-[10px] text-zinc-500 hover:text-zinc-300"
                            title="Copy full UUID"
                          >
                            {copiedId === t.id ? '✓' : 'Copy'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* Tab 2: Dependencies Table */}
        {activeTab === 'dependencies' && (
          <div className="overflow-x-auto rounded border border-zinc-800/80">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-950 text-zinc-400 font-mono text-[11px] border-b border-zinc-800 uppercase">
                <tr>
                  <th className="py-2.5 px-3">Predecessor Task</th>
                  <th className="py-2.5 px-3 text-center">Direction</th>
                  <th className="py-2.5 px-3">Successor Task</th>
                  <th className="py-2.5 px-3">Relational Constraints</th>
                  <th className="py-2.5 px-3 text-right">Edge UUID</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60 bg-zinc-900/30">
                {loadingData ? (
                  Array.from({ length: 4 }).map((_, idx) => (
                    <tr key={idx}>
                      <td colSpan={5} className="p-3">
                        <Skeleton className="h-6 w-full" />
                      </td>
                    </tr>
                  ))
                ) : filteredDependencies.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-zinc-500">
                      No dependency edges found for this project.
                    </td>
                  </tr>
                ) : (
                  filteredDependencies.map((dep) => {
                    const pred = taskMap.get(dep.predecessorTaskId);
                    const succ = taskMap.get(dep.successorTaskId);

                    return (
                      <tr key={dep.id} className="hover:bg-zinc-800/30 transition-colors">
                        <td className="py-2.5 px-3">
                          <div className="font-medium text-zinc-200">
                            {pred ? pred.title : dep.predecessorTaskId.substring(0, 8)}
                          </div>
                          <div className="text-[10px] font-mono text-zinc-500">
                            ID: {dep.predecessorTaskId.substring(0, 13)}...
                          </div>
                        </td>
                        <td className="py-2.5 px-3 text-center">
                          <span className="inline-flex items-center justify-center h-6 w-6 rounded bg-zinc-950 border border-zinc-800 text-emerald-400 font-bold">
                            →
                          </span>
                        </td>
                        <td className="py-2.5 px-3">
                          <div className="font-medium text-zinc-200">
                            {succ ? succ.title : dep.successorTaskId.substring(0, 8)}
                          </div>
                          <div className="text-[10px] font-mono text-zinc-500">
                            ID: {dep.successorTaskId.substring(0, 13)}...
                          </div>
                        </td>
                        <td className="py-2.5 px-3">
                          <div className="flex items-center gap-1.5">
                            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-emerald-950/40 text-emerald-400 border border-emerald-500/20">
                              UNIQUE(pred, succ)
                            </span>
                            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-zinc-950 text-zinc-400 border border-zinc-800">
                              CHECK pred &lt;&gt; succ
                            </span>
                          </div>
                        </td>
                        <td className="py-2.5 px-3 text-right font-mono text-[11px]">
                          <div className="flex items-center justify-end gap-1.5">
                            <code className="text-zinc-400 bg-zinc-950 px-1.5 py-0.5 rounded border border-zinc-800">
                              {dep.id.substring(0, 8)}...
                            </code>
                            <button
                              onClick={() => handleCopy(dep.id)}
                              className="text-[10px] text-zinc-500 hover:text-zinc-300"
                            >
                              {copiedId === dep.id ? '✓' : 'Copy'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* Tab 3: Projects Table */}
        {activeTab === 'projects' && (
          <div className="overflow-x-auto rounded border border-zinc-800/80">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-950 text-zinc-400 font-mono text-[11px] border-b border-zinc-800 uppercase">
                <tr>
                  <th className="py-2.5 px-3">Project Name</th>
                  <th className="py-2.5 px-3">Description</th>
                  <th className="py-2.5 px-3">Created At</th>
                  <th className="py-2.5 px-3 text-right">UUID PK</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60 bg-zinc-900/30">
                {projects.map((proj) => (
                  <tr
                    key={proj.id}
                    className={`hover:bg-zinc-800/30 transition-colors ${
                      proj.id === selectedProjectId ? 'bg-zinc-800/40' : ''
                    }`}
                  >
                    <td className="py-2.5 px-3">
                      <div className="font-semibold text-zinc-200 flex items-center gap-2">
                        <span>{proj.name}</span>
                        {proj.id === selectedProjectId && (
                          <Badge variant="ready" className="text-[9px] py-0 px-1.5">
                            Active
                          </Badge>
                        )}
                      </div>
                    </td>
                    <td className="py-2.5 px-3 text-zinc-400 max-w-sm">
                      <p className="line-clamp-2">{proj.description || '—'}</p>
                    </td>
                    <td className="py-2.5 px-3 font-mono text-[11px] text-zinc-500">
                      {proj.createdAt ? new Date(proj.createdAt).toLocaleString() : '—'}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono text-[11px]">
                      <div className="flex items-center justify-end gap-1.5">
                        <code className="text-zinc-400 bg-zinc-950 px-1.5 py-0.5 rounded border border-zinc-800">
                          {proj.id.substring(0, 8)}...
                        </code>
                        <button
                          onClick={() => handleCopy(proj.id)}
                          className="text-[10px] text-zinc-500 hover:text-zinc-300"
                        >
                          {copiedId === proj.id ? '✓' : 'Copy'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Relational Schema & Concurrency Invariants Reference */}
      <Card className="space-y-4">
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <div>
            <h2 className="text-sm font-semibold text-zinc-100">Relational Schema &amp; Concurrency Invariants</h2>
            <p className="text-xs text-zinc-400">Enforced via Flyway SQL migrations and JPA/Hibernate domain mapping</p>
          </div>
          <span className="text-xs font-mono text-zinc-500">PostgreSQL 16 + JPA</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-1">
          <div className="p-3.5 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <span className="text-xs text-zinc-300 font-medium">UUID Primary Keys</span>
            <p className="text-[11px] text-zinc-400 leading-normal">
              PostgreSQL <code className="text-zinc-300">UUID PRIMARY KEY</code> avoids enumeration attacks and simplifies cross-environment identification.
            </p>
          </div>

          <div className="p-3.5 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <span className="text-xs text-zinc-300 font-medium">JPA Optimistic Locking</span>
            <p className="text-[11px] text-zinc-400 leading-normal">
              JPA/Hibernate <code className="text-zinc-300">@Version</code> on the database <code className="text-zinc-300">version BIGINT</code> column prevents lost updates and concurrent overwrite collisions.
            </p>
          </div>

          <div className="p-3.5 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <span className="text-xs text-zinc-300 font-medium">Database Constraints</span>
            <p className="text-[11px] text-zinc-400 leading-normal">
              PostgreSQL CHECK constraint ensures <code className="text-zinc-300">predecessor_task_id &lt;&gt; successor_task_id</code>, and UNIQUE constraint prevents duplicate edges.
            </p>
          </div>

          <div className="p-3.5 rounded border border-zinc-800/60 bg-zinc-950/40 space-y-2">
            <span className="text-xs text-zinc-300 font-medium">Cascade Protection</span>
            <p className="text-[11px] text-zinc-400 leading-normal">
              Foreign key <code className="text-zinc-300">ON DELETE CASCADE</code> guarantees deleting a task transactionally removes all incident dependency edges.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
};
