'use client';

import React, { useState, useEffect } from 'react';
import { Task, DependencySuggestion } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { aiApi, ApiClientError } from '@/lib/api';

interface AiSuggestionsModalProps {
  isOpen: boolean;
  task: Task | null;
  onClose: () => void;
  onAcceptSuggestion: (predecessorTaskId: string, successorTaskId: string) => Promise<boolean>;
}

export const AiSuggestionsModal: React.FC<AiSuggestionsModalProps> = ({
  isOpen,
  task,
  onClose,
  onAcceptSuggestion,
}) => {
  const [suggestions, setSuggestions] = useState<DependencySuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [acceptingKey, setAcceptingKey] = useState<string | null>(null);
  const [unavailableMessage, setUnavailableMessage] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen || !task) {
      setSuggestions([]);
      setUnavailableMessage(null);
      setErrorMsg(null);
      return;
    }

    const fetchSuggestions = async () => {
      setLoading(true);
      setUnavailableMessage(null);
      setErrorMsg(null);
      try {
        const response = await aiApi.getDependencySuggestions(task.id);
        setSuggestions(response.suggestions || []);
        if (!response.suggestions || response.suggestions.length === 0) {
          setUnavailableMessage('No dependency suggestions found for this task.');
        }
      } catch (err: unknown) {
        if (err instanceof ApiClientError) {
          if (
            err.code === 'AI_SERVICE_UNAVAILABLE' ||
            err.code === 'AI_DISABLED' ||
            err.status === 503 ||
            err.status === 500
          ) {
            setUnavailableMessage('AI suggestions are currently unavailable.');
          } else {
            setUnavailableMessage(err.message || 'AI suggestions are currently unavailable.');
          }
        } else {
          setUnavailableMessage('AI suggestions are currently unavailable.');
        }
      } finally {
        setLoading(false);
      }
    };

    fetchSuggestions();
  }, [isOpen, task]);

  if (!task) return null;

  const handleDismiss = (index: number) => {
    setSuggestions((prev) => prev.filter((_, i) => i !== index));
  };

  const handleAccept = async (suggestion: DependencySuggestion, index: number) => {
    const key = `${suggestion.predecessor.taskId}-${suggestion.successor.taskId}`;
    setAcceptingKey(key);
    setErrorMsg(null);
    try {
      const ok = await onAcceptSuggestion(
        suggestion.predecessor.taskId,
        suggestion.successor.taskId
      );
      if (ok) {
        // Remove from list upon successful backend acceptance
        setSuggestions((prev) => prev.filter((_, i) => i !== index));
      }
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to accept suggestion');
    } finally {
      setAcceptingKey(null);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="AI Dependency Suggestions"
      description={`AI suggestions for "${task.title}". Review before adding.`}
      maxWidth="lg"
    >
      <div className="space-y-4">
        {/* Trust Model Notice */}
        <div className="p-3 rounded-lg bg-zinc-950 border border-amber-500/30 text-xs text-zinc-300 space-y-1">
          <div className="flex items-center gap-1.5 font-semibold text-amber-400">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>AI Suggestion — Review before adding</span>
          </div>
          <p className="text-[11px] text-zinc-400 leading-relaxed">
            Candidate dependency proposals generated via semantic analysis. Accepting a suggestion validates it through the backend DAG engine.
          </p>
        </div>

        {errorMsg && (
          <div className="p-2.5 rounded-lg bg-rose-950/40 border border-rose-800/40 text-rose-300 text-xs">
            {errorMsg}
          </div>
        )}

        {/* Loading State */}
        {loading && (
          <div className="py-12 flex flex-col items-center justify-center space-y-2 text-center text-xs text-zinc-400 font-mono">
            <div className="h-5 w-5 border-2 border-zinc-600 border-t-amber-400 rounded-full animate-spin" />
            <span>Analyzing workflow semantics & task context...</span>
          </div>
        )}

        {/* Unavailable or Empty State */}
        {!loading && unavailableMessage && (
          <div className="p-6 rounded-lg bg-zinc-950/80 border border-zinc-800 text-center space-y-2">
            <div className="text-zinc-400 text-xs font-mono">{unavailableMessage}</div>
            <p className="text-[11px] text-zinc-500">
              The board and manual dependency creation remain fully functional.
            </p>
          </div>
        )}

        {/* Suggestions List */}
        {!loading && suggestions.length > 0 && (
          <div className="space-y-3">
            {suggestions.map((suggestion, index) => {
              const key = `${suggestion.predecessor.taskId}-${suggestion.successor.taskId}`;
              const isAccepting = acceptingKey === key;
              const confidencePct = Math.round(suggestion.confidence * 100);

              return (
                <div
                  key={key}
                  className="p-3.5 rounded-lg bg-zinc-950 border border-zinc-800/80 space-y-3"
                >
                  {/* Visual Edge Diagram */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="flex items-center gap-2 text-xs">
                      <span className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-700 font-medium text-zinc-200 truncate max-w-[200px]">
                        {suggestion.predecessor.title}
                      </span>
                      <span className="text-amber-400 font-bold">→</span>
                      <span className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-700 font-medium text-zinc-200 truncate max-w-[200px]">
                        {suggestion.successor.title}
                      </span>
                    </div>

                    <span className="text-xs font-mono px-2 py-0.5 rounded-full bg-amber-950/60 border border-amber-500/30 text-amber-400 self-start sm:self-auto">
                      {confidencePct}% confidence
                    </span>
                  </div>

                  {/* Reason */}
                  {suggestion.reason && (
                    <div className="p-2 rounded bg-zinc-900/60 border border-zinc-800 text-[11px] text-zinc-300 italic leading-relaxed">
                      &ldquo;{suggestion.reason}&rdquo;
                    </div>
                  )}

                  {/* Action Buttons */}
                  <div className="flex items-center justify-end gap-2 pt-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      type="button"
                      disabled={isAccepting}
                      onClick={() => handleDismiss(index)}
                      className="text-xs text-zinc-400 hover:text-zinc-200"
                    >
                      Dismiss
                    </Button>
                    <Button
                      variant="primary"
                      size="sm"
                      type="button"
                      disabled={isAccepting}
                      onClick={() => handleAccept(suggestion, index)}
                      className="text-xs"
                    >
                      {isAccepting ? 'Validating & Adding...' : 'Accept Suggestion'}
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        <div className="pt-2 border-t border-zinc-800/80 flex justify-end">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
};
