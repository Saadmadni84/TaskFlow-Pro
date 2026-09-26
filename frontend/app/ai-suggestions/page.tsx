import React from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';

export default function AiSuggestionsPage() {
  return (
    <div className="max-w-6xl mx-auto space-y-8">
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400 uppercase tracking-widest">
            Future Module
          </span>
          <span className="text-zinc-600">/</span>
          <span className="text-xs font-mono text-zinc-400">Human-In-The-Loop AI</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
          AI Dependency Suggestions
        </h1>
        <p className="text-sm text-zinc-400 max-w-3xl leading-relaxed">
          AI suggestions recommend logical dependencies between tasks based on task titles, descriptions, and semantic requirements.
          Every suggestion is strictly human-in-the-loop and validated by the backend DAG engine before creation.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="space-y-2">
          <Badge variant="neutral">Architecture</Badge>
          <div className="text-sm font-semibold text-zinc-200">Advisory Only</div>
          <p className="text-xs text-zinc-400">
            AI never mutates the database directly. Suggestions must be approved by a human user.
          </p>
        </Card>

        <Card className="space-y-2">
          <Badge variant="neutral">Verification</Badge>
          <div className="text-sm font-semibold text-zinc-200">DAG Engine Validation</div>
          <p className="text-xs text-zinc-400">
            Suggested edges are pre-checked for cycles before being presented to the user.
          </p>
        </Card>

        <Card className="space-y-2">
          <Badge variant="neutral">UX</Badge>
          <div className="text-sm font-semibold text-zinc-200">One-Click Acceptance</div>
          <p className="text-xs text-zinc-400">
            Approve or reject suggested dependency connections directly from the task detail modal.
          </p>
        </Card>
      </div>
    </div>
  );
}
