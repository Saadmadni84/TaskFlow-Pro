'use client';

import React, { useEffect } from 'react';
import { Button } from '@/components/ui/Button';

export default function ErrorBoundary({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Operational UI error logging
    console.error('TaskFlow Pro UI Error:', {
      name: error.name,
      message: error.message,
      digest: error.digest,
    });
  }, [error]);

  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center text-center p-6 space-y-4">
      <div className="h-12 w-12 rounded-full bg-rose-950/50 border border-rose-800/60 flex items-center justify-center text-rose-400">
        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
      </div>

      <div className="space-y-1">
        <h2 className="text-lg font-semibold text-zinc-100">Something went wrong</h2>
        <p className="text-xs text-zinc-400 max-w-md">
          An unexpected interface error occurred. You can retry the operation or refresh the page.
        </p>
        {error.digest && (
          <p className="text-[11px] font-mono text-zinc-500 pt-1">
            Reference ID: <span className="text-zinc-400">{error.digest}</span>
          </p>
        )}
      </div>

      <div className="flex gap-3">
        <Button variant="outline" size="sm" onClick={() => window.location.reload()}>
          Refresh Page
        </Button>
        <Button variant="primary" size="sm" onClick={() => reset()}>
          Try Again
        </Button>
      </div>
    </div>
  );
}
