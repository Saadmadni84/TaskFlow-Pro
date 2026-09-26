'use client';

import React, { useState, useMemo, useEffect } from 'react';
import Link from 'next/link';
import { Task, DependencySuggestion } from '@/types';
import { useProjects } from '@/hooks/useProjects';
import { useProjectBoard } from '@/hooks/useProjectBoard';
import { aiApi, ApiClientError } from '@/lib/api';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

export const AiSuggestionWorkspace: React.FC = () => {
  // 1. Project Management
  const {
    projects,
    selectedProjectId,
    setSelectedProjectId,
    loading: projectsLoading,
  } = useProjects();

  // 2. Project Board (Tasks, Dependencies, Readiness)
  const {
    tasks,
    prerequisitesByTask,
    loading: boardLoading,
    acceptAiSuggestion,
  } = useProjectBoard(selectedProjectId);

  // 3. Target Task Selection
  const [selectedTaskId, setSelectedTaskId] = useState<string>('');
  const selectedTask = useMemo(() => {
    return tasks.find((t) => t.id === selectedTaskId) || null;
  }, [tasks, selectedTaskId]);

  // Reset selected task if project changes
  useEffect(() => {
    setSelectedTaskId('');
    setSuggestions([]);
    setGeneratedTaskId(null);
    setAiDisabled(false);
    setAiError(null);
  }, [selectedProjectId]);

  // Current prerequisites of the selected task
  const currentPrerequisites = useMemo(() => {
    if (!selectedTask) return [];
    const prereqIds = prerequisitesByTask.get(selectedTask.id) || [];
    return prereqIds
      .map((id) => tasks.find((t) => t.id === id))
      .filter((t): t is Task => t !== undefined);
  }, [selectedTask, prerequisitesByTask, tasks]);

  // 4. AI Suggestion State
  const [isGenerating, setIsGenerating] = useState(false);
  const [suggestions, setSuggestions] = useState<DependencySuggestion[]>([]);
  const [generatedTaskId, setGeneratedTaskId] = useState<string | null>(null);
  const [acceptedSuggestionKeys, setAcceptedSuggestionKeys] = useState<Set<string>>(new Set());
  const [acceptingKey, setAcceptingKey] = useState<string | null>(null);
  const [actionErrors, setActionErrors] = useState<Record<string, string>>({});
  const [aiDisabled, setAiDisabled] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);

  // Trigger AI generation
  const handleGenerate = async () => {
    if (!selectedTask || isGenerating) return;

    setIsGenerating(true);
    setAiDisabled(false);
    setAiError(null);
    setActionErrors({});

    try {
      const response = await aiApi.getDependencySuggestions(selectedTask.id);
      setSuggestions(response.suggestions || []);
      setGeneratedTaskId(selectedTask.id);
      // Reset accepted keys for newly generated batch
      setAcceptedSuggestionKeys(new Set());
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        if (
          err.code === 'AI_DISABLED' ||
          err.message?.includes('disabled') ||
          (err.status === 503 && err.code === 'AI_DISABLED')
        ) {
          setAiDisabled(true);
        } else if (err.status === 429) {
          setAiError('AI rate limit reached. Please wait a moment before trying again.');
        } else {
          setAiError(err.message || 'AI suggestions are temporarily unavailable.');
        }
      } else {
        setAiError('Failed to communicate with AI suggestion engine. Please try again.');
      }
      setSuggestions([]);
      setGeneratedTaskId(selectedTask.id);
    } finally {
      setIsGenerating(false);
    }
  };

  // Accept a single suggestion
  const handleAccept = async (suggestion: DependencySuggestion) => {
    const key = `${suggestion.predecessor.taskId}->${suggestion.successor.taskId}`;
    setAcceptingKey(key);
    setActionErrors((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });

    try {
      const ok = await acceptAiSuggestion(
        suggestion.predecessor.taskId,
        suggestion.successor.taskId
      );

      if (ok) {
        setAcceptedSuggestionKeys((prev) => new Set(prev).add(key));
      } else {
        setActionErrors((prev) => ({
          ...prev,
          [key]: 'Dependency rejected by DAG validation engine (possible cycle or duplicate).',
        }));
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to accept dependency';
      setActionErrors((prev) => ({
        ...prev,
        [key]: msg,
      }));
    } finally {
      setAcceptingKey(null);
    }
  };

  // Dismiss a single suggestion from active view
  const handleDismiss = (suggestion: DependencySuggestion) => {
    const key = `${suggestion.predecessor.taskId}->${suggestion.successor.taskId}`;
    setSuggestions((prev) =>
      prev.filter(
        (s) => `${s.predecessor.taskId}->${s.successor.taskId}` !== key
      )
    );
  };

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      {/* 1. Header & Advisory Notice */}
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            AI Assistant
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-emerald-400">Human-In-The-Loop DAG</span>
          <span className="text-zinc-600">/</span>
          <Badge variant="neutral" className="text-[10px] tracking-normal font-mono">
            Deterministic Engine Authority
          </Badge>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-100">
              AI Dependency Suggestions
            </h1>
            <p className="text-sm text-zinc-400 max-w-2xl leading-relaxed mt-1">
              Find likely prerequisites for a task using AI. Suggestions are strictly advisory and require explicit human approval before being validated and persisted by the deterministic DAG engine.
            </p>
          </div>

          {/* Project Selector */}
          {projects.length > 0 && (
            <div className="flex items-center gap-2 self-start sm:self-auto bg-zinc-900/80 border border-zinc-800 rounded-lg p-1.5 px-3">
              <span className="text-xs text-zinc-400 font-mono">Project:</span>
              <select
                aria-label="Select project"
                value={selectedProjectId || ''}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                className="bg-transparent text-xs text-zinc-200 font-medium focus:outline-none cursor-pointer"
              >
                {projects.map((p) => (
                  <option key={p.id} value={p.id} className="bg-zinc-900 text-zinc-200">
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      </div>

      {/* 2. Workspace: Target Task Selection & Context */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Task Picker & Action */}
        <div className="lg:col-span-5 space-y-5">
          <Card className="space-y-4">
            <div className="space-y-1.5">
              <label
                htmlFor="target-task-select"
                className="block text-xs font-mono font-semibold uppercase tracking-wider text-zinc-300"
              >
                Select Target Task
              </label>
              <p className="text-xs text-zinc-400">
                Choose a task in this project to analyze for potential prerequisites.
              </p>
            </div>

            {boardLoading || projectsLoading ? (
              <div className="py-4 flex items-center justify-center text-xs text-zinc-500 font-mono">
                Loading project tasks...
              </div>
            ) : tasks.length === 0 ? (
              <div className="p-4 rounded-lg bg-zinc-950/60 border border-zinc-800 text-center space-y-2">
                <p className="text-xs text-zinc-400">No tasks available in this project.</p>
                <p className="text-[11px] text-zinc-500">
                  Create tasks first to generate dependency suggestions.
                </p>
                <Link href="/kanban">
                  <Button variant="outline" size="sm" className="mt-2 text-xs">
                    Go to Kanban Board
                  </Button>
                </Link>
              </div>
            ) : (
              <div className="space-y-4">
                <select
                  id="target-task-select"
                  aria-label="Target task"
                  value={selectedTaskId}
                  onChange={(e) => {
                    setSelectedTaskId(e.target.value);
                    setSuggestions([]);
                    setGeneratedTaskId(null);
                    setAiDisabled(false);
                    setAiError(null);
                  }}
                  className="w-full px-3 py-2 bg-zinc-950 border border-zinc-800 rounded-lg text-xs text-zinc-200 focus:outline-none focus:ring-1 focus:ring-emerald-500 transition-colors"
                >
                  <option value="">-- Choose a task to analyze --</option>
                  {tasks.map((task) => (
                    <option key={task.id} value={task.id}>
                      {task.title} [{task.workflowStatus}]
                    </option>
                  ))}
                </select>

                <Button
                  id="generate-suggestions-btn"
                  variant="primary"
                  size="md"
                  disabled={!selectedTaskId || isGenerating}
                  onClick={handleGenerate}
                  className="w-full text-xs font-semibold flex items-center justify-center gap-2 shadow-sm"
                >
                  {isGenerating ? (
                    <>
                      <div className="h-4 w-4 border-2 border-zinc-900 border-t-emerald-600 rounded-full animate-spin" />
                      <span>Analyzing project dependencies...</span>
                    </>
                  ) : (
                    <>
                      <svg className="w-4 h-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                      <span>
                        {generatedTaskId === selectedTaskId && suggestions.length > 0
                          ? 'Regenerate Suggestions'
                          : 'Generate AI Suggestions'}
                      </span>
                    </>
                  )}
                </Button>

                {generatedTaskId === selectedTaskId && suggestions.length > 0 && (
                  <p className="text-[11px] text-zinc-500 text-center italic">
                    Regenerating will replace current unaccepted recommendations.
                  </p>
                )}
              </div>
            )}
          </Card>

          {/* Quick Explanatory Callout */}
          <div className="p-3.5 rounded-lg bg-zinc-950/60 border border-zinc-800/80 text-xs text-zinc-400 space-y-1.5">
            <div className="flex items-center gap-1.5 font-semibold text-zinc-300">
              <span className="text-amber-400">ℹ</span>
              <span>Advisory AI Layer</span>
            </div>
            <p className="text-[11px] leading-relaxed text-zinc-400">
              Suggestions analyze semantic relationships across project task titles and descriptions. The deterministic DAG engine performs cycle validation only upon explicit human acceptance.
            </p>
          </div>
        </div>

        {/* Right Column: Target Task Context Card */}
        <div className="lg:col-span-7">
          {selectedTask ? (
            <Card className="space-y-4 h-full flex flex-col justify-between">
              <div className="space-y-3">
                <div className="flex items-center justify-between gap-2 border-b border-zinc-800/60 pb-3">
                  <span className="text-xs font-mono font-semibold uppercase tracking-wider text-zinc-400">
                    Target Task Context
                  </span>
                  <div className="flex items-center gap-2">
                    <Badge variant={selectedTask.dependencyStatus === 'READY' ? 'ready' : 'blocked'}>
                      {selectedTask.dependencyStatus}
                    </Badge>
                    <Badge variant="neutral">
                      {selectedTask.workflowStatus}
                    </Badge>
                  </div>
                </div>

                <div className="space-y-2">
                  <h2 className="text-base font-semibold text-zinc-100">
                    {selectedTask.title}
                  </h2>
                  <div className="text-xs text-zinc-400 leading-relaxed bg-zinc-950/60 border border-zinc-800/60 rounded p-2.5 min-h-[50px]">
                    {selectedTask.description || (
                      <span className="text-zinc-500 italic">No description provided for this task.</span>
                    )}
                  </div>
                </div>

                {/* Current direct prerequisites */}
                <div className="space-y-1.5 pt-1">
                  <div className="text-xs font-mono text-zinc-400 uppercase tracking-wide">
                    Current Prerequisites ({currentPrerequisites.length}):
                  </div>
                  {currentPrerequisites.length === 0 ? (
                    <p className="text-xs text-zinc-500 italic">
                      None — this task currently has no prerequisite dependencies.
                    </p>
                  ) : (
                    <ul className="space-y-1.5">
                      {currentPrerequisites.map((prereq) => (
                        <li
                          key={prereq.id}
                          className="flex items-center justify-between text-xs p-2 rounded bg-zinc-950/80 border border-zinc-800/60 text-zinc-200"
                        >
                          <span className="font-medium truncate max-w-[320px]">
                            • {prereq.title}
                          </span>
                          <span className="text-[10px] font-mono text-zinc-500">
                            {prereq.workflowStatus}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>

              <div className="pt-3 border-t border-zinc-800/60 text-[11px] font-mono text-zinc-500 flex items-center justify-between">
                <span>Task ID: {selectedTask.id.substring(0, 8)}...</span>
                <span>Duration: {selectedTask.durationDays} day(s)</span>
              </div>
            </Card>
          ) : (
            <Card className="h-full flex flex-col items-center justify-center p-8 text-center space-y-2 text-zinc-500 border-dashed">
              <svg className="w-8 h-8 text-zinc-600 mb-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
              <p className="text-xs font-medium text-zinc-400">Select a task to get AI dependency suggestions.</p>
              <p className="text-[11px] text-zinc-500 max-w-sm">
                The context card will show the selected task&apos;s details, status, and existing prerequisites.
              </p>
            </Card>
          )}
        </div>
      </div>

      {/* 3. Suggestions Section */}
      <div className="space-y-4 pt-2">
        <div className="flex items-center justify-between border-b border-zinc-800 pb-2">
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-zinc-100">AI Suggestions</h2>
            {generatedTaskId && suggestions.length > 0 && (
              <span className="text-xs font-mono text-zinc-400 bg-zinc-900 px-2 py-0.5 rounded-full border border-zinc-800">
                {suggestions.length} candidate{suggestions.length === 1 ? '' : 's'}
              </span>
            )}
          </div>
          {generatedTaskId && (
            <span className="text-xs text-zinc-400 font-mono">
              Target: {selectedTask?.title || 'Unknown'}
            </span>
          )}
        </div>

        {/* AI Disabled State Banner */}
        {aiDisabled && (
          <Card className="p-6 border-amber-500/30 bg-amber-950/20 text-center space-y-3">
            <div className="inline-flex items-center gap-2 text-sm font-semibold text-amber-400">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <span>AI suggestions are currently disabled.</span>
            </div>
            <p className="text-xs text-zinc-300 max-w-lg mx-auto leading-relaxed">
              The AI dependency suggestion engine is currently toggled off in the application configuration. You can continue creating and managing dependencies manually via the Kanban board or DAG graph.
            </p>
            <div className="pt-2">
              <Link href="/kanban">
                <Button variant="primary" size="sm" className="text-xs">
                  Open Kanban Board
                </Button>
              </Link>
            </div>
          </Card>
        )}

        {/* AI Error / Unavailable Banner */}
        {!aiDisabled && aiError && (
          <Card className="p-5 border-rose-800/40 bg-rose-950/20 text-center space-y-2">
            <div className="text-xs font-semibold text-rose-300">
              AI suggestions are temporarily unavailable.
            </div>
            <p className="text-xs text-zinc-400 max-w-lg mx-auto">
              {aiError} You can still create dependencies manually.
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={handleGenerate}
              className="mt-2 text-xs"
            >
              Try Again
            </Button>
          </Card>
        )}

        {/* Loading Indicator */}
        {isGenerating && (
          <Card className="py-12 flex flex-col items-center justify-center space-y-3 text-center border-dashed">
            <div className="h-6 w-6 border-2 border-zinc-700 border-t-emerald-500 rounded-full animate-spin" />
            <div className="space-y-1">
              <div className="text-xs font-mono font-medium text-zinc-300">
                Analyzing project dependencies...
              </div>
              <p className="text-[11px] text-zinc-500">
                Evaluating candidate relationships against project context and workflow constraints.
              </p>
            </div>
          </Card>
        )}

        {/* Empty Result State */}
        {!isGenerating && !aiDisabled && !aiError && generatedTaskId && suggestions.length === 0 && (
          <Card className="p-8 text-center space-y-3 border-dashed">
            <div className="text-sm font-semibold text-zinc-300">
              No dependency suggestions found.
            </div>
            <p className="text-xs text-zinc-400 max-w-md mx-auto leading-relaxed">
              The AI could not identify a strong prerequisite relationship for this task based on current candidate tasks and existing connections.
            </p>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleGenerate}
              className="text-xs"
            >
              Generate Again
            </Button>
          </Card>
        )}

        {/* Suggestions List */}
        {!isGenerating && !aiDisabled && suggestions.length > 0 && (
          <div className="space-y-4">
            {suggestions.map((suggestion) => {
              const key = `${suggestion.predecessor.taskId}->${suggestion.successor.taskId}`;
              const isAccepted = acceptedSuggestionKeys.has(key);
              const isAccepting = acceptingKey === key;
              const error = actionErrors[key];
              const confidencePct = Math.round(suggestion.confidence * 100);

              return (
                <Card
                  key={key}
                  className={`space-y-4 transition-all ${
                    isAccepted
                      ? 'border-emerald-500/40 bg-emerald-950/10'
                      : 'hover:border-zinc-700'
                  }`}
                >
                  {/* Top Bar: Direction and Confidence */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-zinc-800/60 pb-3">
                    <div className="space-y-1">
                      <span className="text-[10px] font-mono text-zinc-500 uppercase tracking-wider block">
                        Suggested Dependency Direction
                      </span>
                      {/* Explicit Direction Predecessor -> Successor */}
                      <div className="flex items-center gap-2 text-xs">
                        <span className="px-2.5 py-1 rounded bg-zinc-950 border border-zinc-700/80 font-medium text-zinc-200">
                          {suggestion.predecessor.title}
                        </span>
                        <div className="flex flex-col items-center px-1 text-emerald-400 font-bold">
                          <span>↓</span>
                        </div>
                        <span className="px-2.5 py-1 rounded bg-zinc-950 border border-zinc-700/80 font-medium text-zinc-200">
                          {suggestion.successor.title}
                        </span>
                      </div>
                    </div>

                    {/* Confidence Meter */}
                    <div className="flex flex-col items-start sm:items-end gap-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-mono font-medium text-zinc-300">
                          Confidence: {confidencePct}%
                        </span>
                        <div className="w-16 h-2 bg-zinc-800 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-emerald-500 rounded-full"
                            style={{ width: `${confidencePct}%` }}
                          />
                        </div>
                      </div>
                      <span className="text-[10px] text-zinc-500">
                        AI confidence is advisory. Review before accepting.
                      </span>
                    </div>
                  </div>

                  {/* Why this was suggested (Reason) */}
                  <div className="space-y-1">
                    <span className="text-[11px] font-mono text-zinc-400 uppercase tracking-wider block">
                      Why this was suggested
                    </span>
                    <p className="text-xs text-zinc-300 leading-relaxed bg-zinc-950/60 border border-zinc-800/80 rounded p-3 italic">
                      &ldquo;{suggestion.reason}&rdquo;
                    </p>
                  </div>

                  {/* Acceptance Failure Alert */}
                  {error && (
                    <div className="p-2.5 rounded bg-rose-950/40 border border-rose-800/50 text-rose-300 text-xs">
                      {error}
                    </div>
                  )}

                  {/* Action Bar */}
                  <div className="flex items-center justify-between pt-1">
                    <div>
                      {isAccepted && (
                        <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-400 bg-emerald-950/60 px-2.5 py-1 rounded border border-emerald-500/30">
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                          </svg>
                          <span>✓ Dependency added</span>
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      {!isAccepted && (
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={isAccepting}
                          onClick={() => handleDismiss(suggestion)}
                          className="text-xs text-zinc-400 hover:text-zinc-200"
                        >
                          Dismiss
                        </Button>
                      )}

                      <Button
                        variant={isAccepted ? 'outline' : 'primary'}
                        size="sm"
                        disabled={isAccepted || isAccepting}
                        onClick={() => handleAccept(suggestion)}
                        className="text-xs"
                      >
                        {isAccepting ? (
                          <span className="flex items-center gap-1.5">
                            <div className="h-3 w-3 border-2 border-zinc-900 border-t-zinc-400 rounded-full animate-spin" />
                            Validating & Adding...
                          </span>
                        ) : isAccepted ? (
                          'Accepted'
                        ) : (
                          'Accept'
                        )}
                      </Button>
                    </div>
                  </div>
                </Card>
              );
            })}
          </div>
        )}
      </div>

      {/* 4. Secondary Content: How It Works Concept Cards (Section 35) */}
      <div className="pt-6 border-t border-zinc-800/80 space-y-4">
        <div className="space-y-1">
          <h3 className="text-xs font-mono font-semibold uppercase tracking-wider text-zinc-400">
            How AI Suggestions Work
          </h3>
          <p className="text-xs text-zinc-500">
            The AI engine operates strictly as an assistance layer. The deterministic DAG engine remains authoritative.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Card className="space-y-2 bg-zinc-950/60 border-zinc-800/80">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-zinc-200">Advisory Only</span>
              <Badge variant="neutral" className="text-[10px]">Guardrail</Badge>
            </div>
            <p className="text-xs text-zinc-400 leading-relaxed">
              AI suggests; humans approve. The LLM never writes directly to the database or mutates the dependency graph.
            </p>
          </Card>

          <Card className="space-y-2 bg-zinc-950/60 border-zinc-800/80">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-zinc-200">DAG Engine Validation</span>
              <Badge variant="neutral" className="text-[10px]">Authority</Badge>
            </div>
            <p className="text-xs text-zinc-400 leading-relaxed">
              Every accepted dependency is validated by the deterministic DAG engine. Cyclic or duplicate edges are rejected before persistence.
            </p>
          </Card>

          <Card className="space-y-2 bg-zinc-950/60 border-zinc-800/80">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-zinc-200">Human Approval</span>
              <Badge variant="neutral" className="text-[10px]">Control</Badge>
            </div>
            <p className="text-xs text-zinc-400 leading-relaxed">
              Nothing changes until you approve it. Every relationship must be reviewed and accepted individually by the user.
            </p>
          </Card>
        </div>
      </div>
    </div>
  );
};
