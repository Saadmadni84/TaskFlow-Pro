'use client';

import React, { useState, useMemo } from 'react';
import { Task } from '@/types';
import { useProjects } from '@/hooks/useProjects';
import { useProjectBoard } from '@/hooks/useProjectBoard';
import { KanbanBoard } from '@/components/board/KanbanBoard';
import { TaskCreateDialog } from '@/components/task/TaskCreateDialog';
import { TaskEditDialog } from '@/components/task/TaskEditDialog';
import { DependencyModal } from '@/components/dependency/DependencyModal';
import { AiSuggestionsModal } from '@/components/ai/AiSuggestionsModal';
import { CriticalPathModal } from '@/components/criticalpath/CriticalPathModal';
import { TaskDeleteDialog } from '@/components/task/TaskDeleteDialog';
import { ProjectCreateDialog } from '@/components/project/ProjectCreateDialog';
import { BoardSkeleton } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import { DependencyGraphView } from '@/components/graph/DependencyGraphView';

interface KanbanWorkspaceProps {
  initialProjectId?: string;
  initialView?: 'KANBAN' | 'GRAPH';
  initialCriticalMode?: boolean;
  initialSelectedTaskId?: string | null;
}

type FilterState = 'ALL' | 'READY' | 'BLOCKED';

export const KanbanWorkspace: React.FC<KanbanWorkspaceProps> = ({
  initialProjectId,
  initialView = 'KANBAN',
  initialCriticalMode = false,
  initialSelectedTaskId = null,
}) => {
  const [activeView, setActiveView] = useState<'KANBAN' | 'GRAPH'>(initialView);
  // 1. Projects state
  const {
    projects,
    selectedProjectId,
    selectedProject,
    setSelectedProjectId,
    error: projectsError,
    createProject,
    refreshProjects,
  } = useProjects(initialProjectId);

  // 2. Project Board state
  const {
    tasks,
    dependencies,
    prerequisitesByTask,
    dependentsByTask,
    loading: boardLoading,
    error: boardError,
    actionError,
    clearActionError,
    refresh: refreshBoard,
    moveTask,
    createTask,
    updateTask,
    deleteTask,
    addDependency,
    removeDependency,
    previewScheduleImpact,
    getCriticalPath,
    acceptAiSuggestion,
  } = useProjectBoard(selectedProjectId);

  // 3. UI Filters & Search
  const [filter, setFilter] = useState<FilterState>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // 4. Modal States
  const [isCreateTaskOpen, setIsCreateTaskOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [dependencyTask, setDependencyTask] = useState<Task | null>(null);
  const [aiTask, setAiTask] = useState<Task | null>(null);
  const [deletingTaskId, setDeletingTaskId] = useState<string | null>(null);
  const [isCriticalPathOpen, setIsCriticalPathOpen] = useState(false);
  const [isCreateProjectOpen, setIsCreateProjectOpen] = useState(false);
  const [isDeletingTask, setIsDeletingTask] = useState(false);

  // Critical task IDs set for highlighting on board
  const [criticalTaskIds, setCriticalTaskIds] = useState<Set<string>>(new Set());

  // Filtered Tasks
  const filteredTasks = useMemo(() => {
    let result = tasks;

    // Filter by readiness
    if (filter === 'READY') {
      result = result.filter((t) => t.dependencyStatus === 'READY');
    } else if (filter === 'BLOCKED') {
      result = result.filter((t) => t.dependencyStatus === 'BLOCKED');
    }

    // Filter by search query
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter(
        (t) =>
          t.title.toLowerCase().includes(q) ||
          (t.description && t.description.toLowerCase().includes(q))
      );
    }

    return result;
  }, [tasks, filter, searchQuery]);

  // Load critical path tasks when critical path modal opens or on demand
  const handleOpenCriticalPath = async () => {
    setIsCriticalPathOpen(true);
    const cp = await getCriticalPath();
    if (cp && cp.criticalTaskIds) {
      setCriticalTaskIds(new Set(cp.criticalTaskIds));
    }
  };

  const handleConfirmDelete = async (taskId: string) => {
    setIsDeletingTask(true);
    try {
      const ok = await deleteTask(taskId);
      if (ok) {
        setDeletingTaskId(null);
      }
    } finally {
      setIsDeletingTask(false);
    }
  };

  const deletingTaskObject = tasks.find((t) => t.id === deletingTaskId);

  return (
    <div className="space-y-6">
      {/* Top Workspace Header & Project Switcher */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 border-b border-zinc-800/80">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-mono text-emerald-400 uppercase tracking-widest bg-emerald-950/60 border border-emerald-500/30 px-2 py-0.5 rounded">
              Production Kanban
            </span>
            <span className="text-zinc-600">/</span>
            <span className="text-xs font-mono text-zinc-400">Deterministic Workflow UI</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100 mt-1">
            {selectedProject ? selectedProject.name : 'TaskFlow Pro Workspace'}
          </h1>
          {selectedProject?.description && (
            <p className="text-xs text-zinc-400 mt-0.5 leading-relaxed max-w-2xl">
              {selectedProject.description}
            </p>
          )}
        </div>

        {/* Project Selector & Actions */}
        <div className="flex flex-wrap items-center gap-2">
          {projects.length > 0 && (
            <select
              value={selectedProjectId || ''}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              className="px-3 py-1.5 text-xs rounded-md bg-zinc-900 border border-zinc-800 text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-400"
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
            onClick={() => setIsCreateProjectOpen(true)}
            className="text-xs"
          >
            + New Project
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              refreshProjects();
              refreshBoard();
            }}
            className="text-xs text-zinc-400 hover:text-zinc-200"
            title="Refresh board from backend"
          >
            <svg className="w-3.5 h-3.5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </Button>
        </div>
      </div>

      {/* View Switcher: Kanban vs Visual DAG */}
      <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
        <div className="flex items-center bg-zinc-900 border border-zinc-800 rounded-lg p-0.5 text-xs font-mono">
          <button
            type="button"
            onClick={() => setActiveView('KANBAN')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md transition-all ${
              activeView === 'KANBAN'
                ? 'bg-zinc-800 text-emerald-400 font-semibold shadow-sm border border-emerald-500/20'
                : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 10h16M4 14h16M4 18h16" />
            </svg>
            <span>Kanban Board</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveView('GRAPH')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md transition-all ${
              activeView === 'GRAPH'
                ? 'bg-zinc-800 text-emerald-400 font-semibold shadow-sm border border-emerald-500/20'
                : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
            </svg>
            <span>Visual DAG Graph</span>
          </button>
        </div>

        <div className="text-[11px] font-mono text-zinc-500 hidden sm:block">
          {activeView === 'KANBAN' ? 'Drag & Drop Task Workflow' : 'Deterministic Topological Projection'}
        </div>
      </div>

      {/* Global Action / Concurrency / Cycle Error Banner */}
      {actionError && (
        <div className="p-3 rounded-lg bg-rose-950/60 border border-rose-800/80 text-rose-200 text-xs flex items-center justify-between animate-in fade-in duration-150">
          <div className="flex items-center gap-2">
            <svg className="w-4 h-4 text-rose-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{actionError}</span>
          </div>
          <button
            onClick={clearActionError}
            className="text-rose-400 hover:text-rose-100 font-mono text-xs px-2 py-0.5 rounded"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Backend Connection Error */}
      {(projectsError || boardError) && (
        <div className="p-4 rounded-lg bg-amber-950/40 border border-amber-800/60 text-amber-200 text-xs flex items-start gap-3">
          <svg className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <div>
            <div className="font-semibold">Unable to connect to TaskFlow Pro backend.</div>
            <div className="mt-0.5 text-zinc-400">
              Check that the backend is running on http://localhost:8080 and try again.
            </div>
          </div>
        </div>
      )}

      {/* Toolbar: Actions, Filters, Search (only in Kanban view) */}
      {activeView === 'KANBAN' && (
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        {/* Left Toolbar Actions */}
        <div className="flex items-center gap-2">
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsCreateTaskOpen(true)}
            disabled={!selectedProjectId}
            className="text-xs"
          >
            + New Task
          </Button>

          <Button
            variant="secondary"
            size="sm"
            onClick={handleOpenCriticalPath}
            disabled={!selectedProjectId}
            className="text-xs border-amber-500/30 text-amber-300 hover:bg-amber-950/40"
          >
            Critical Path
          </Button>
        </div>

        {/* Right Toolbar: Readiness Filters & Search */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Readiness Filters */}
          <div className="flex items-center bg-zinc-900 border border-zinc-800 rounded-md p-0.5 text-xs">
            <button
              type="button"
              onClick={() => setFilter('ALL')}
              className={`px-2.5 py-1 rounded text-xs transition-colors ${
                filter === 'ALL'
                  ? 'bg-zinc-800 text-zinc-100 font-medium'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              All ({tasks.length})
            </button>
            <button
              type="button"
              onClick={() => setFilter('READY')}
              className={`px-2.5 py-1 rounded text-xs transition-colors ${
                filter === 'READY'
                  ? 'bg-emerald-950/80 text-emerald-400 font-medium border border-emerald-500/30'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Ready ({tasks.filter((t) => t.dependencyStatus === 'READY').length})
            </button>
            <button
              type="button"
              onClick={() => setFilter('BLOCKED')}
              className={`px-2.5 py-1 rounded text-xs transition-colors ${
                filter === 'BLOCKED'
                  ? 'bg-rose-950/80 text-rose-400 font-medium border border-rose-500/30'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Blocked ({tasks.filter((t) => t.dependencyStatus === 'BLOCKED').length})
            </button>
          </div>

          {/* Search Input */}
          <div className="relative">
            <input
              type="text"
              placeholder="Search tasks..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-40 sm:w-48 px-2.5 py-1.5 pl-7 text-xs rounded-md bg-zinc-900 border border-zinc-800 text-zinc-200 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-zinc-400"
            />
            <svg
              className="w-3.5 h-3.5 text-zinc-500 absolute left-2 top-2"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
        </div>
      </div>
      )}

      {/* Main Content Area: Kanban vs Visual DAG */}
      {boardLoading && tasks.length === 0 ? (
        <BoardSkeleton />
      ) : !selectedProjectId ? (
        <div className="h-80 rounded-xl border border-dashed border-zinc-800 bg-zinc-950/40 flex flex-col items-center justify-center p-8 text-center space-y-3">
          <p className="text-sm text-zinc-400">No project selected.</p>
          <Button variant="primary" size="sm" onClick={() => setIsCreateProjectOpen(true)}>
            Create First Project
          </Button>
        </div>
      ) : activeView === 'GRAPH' ? (
        <DependencyGraphView
          projectId={selectedProjectId}
          initialCriticalMode={initialCriticalMode}
          initialSelectedTaskId={initialSelectedTaskId}
          onNavigateToKanban={(taskId) => {
            setActiveView('KANBAN');
            const found = tasks.find((t) => t.id === taskId);
            if (found) setEditingTask(found);
          }}
          onCreateTask={() => setIsCreateTaskOpen(true)}
        />
      ) : (
        <KanbanBoard
          tasks={filteredTasks}
          prerequisitesByTask={prerequisitesByTask}
          dependentsByTask={dependentsByTask}
          criticalTaskIds={criticalTaskIds}
          onMoveTask={moveTask}
          onEditTask={(t) => setEditingTask(t)}
          onOpenDependencies={(t) => setDependencyTask(t)}
          onOpenAiSuggestions={(t) => setAiTask(t)}
          onDeleteTask={(id) => setDeletingTaskId(id)}
          onCreateTaskClick={() => setIsCreateTaskOpen(true)}
        />
      )}

      {/* Modals & Dialogs */}
      {selectedProjectId && (
        <TaskCreateDialog
          isOpen={isCreateTaskOpen}
          projectId={selectedProjectId}
          onClose={() => setIsCreateTaskOpen(false)}
          onSubmit={async (payload) => {
            await createTask(payload);
          }}
        />
      )}

      {editingTask && (
        <TaskEditDialog
          isOpen={!!editingTask}
          task={editingTask}
          onClose={() => setEditingTask(null)}
          onUpdate={updateTask}
          onPreviewImpact={previewScheduleImpact}
        />
      )}

      {dependencyTask && (
        <DependencyModal
          isOpen={!!dependencyTask}
          task={dependencyTask}
          allTasks={tasks}
          dependencies={dependencies}
          onClose={() => setDependencyTask(null)}
          onAddDependency={addDependency}
          onRemoveDependency={removeDependency}
        />
      )}

      {aiTask && (
        <AiSuggestionsModal
          isOpen={!!aiTask}
          task={aiTask}
          onClose={() => setAiTask(null)}
          onAcceptSuggestion={acceptAiSuggestion}
        />
      )}

      {isCriticalPathOpen && selectedProject && (
        <CriticalPathModal
          isOpen={isCriticalPathOpen}
          projectName={selectedProject.name}
          onClose={() => setIsCriticalPathOpen(false)}
          fetchCriticalPath={getCriticalPath}
        />
      )}

      <TaskDeleteDialog
        isOpen={!!deletingTaskId}
        taskId={deletingTaskId}
        taskTitle={deletingTaskObject?.title || 'Selected task'}
        onClose={() => setDeletingTaskId(null)}
        onConfirm={handleConfirmDelete}
        deleting={isDeletingTask}
      />

      <ProjectCreateDialog
        isOpen={isCreateProjectOpen}
        onClose={() => setIsCreateProjectOpen(false)}
        onSubmit={createProject}
      />
    </div>
  );
};
