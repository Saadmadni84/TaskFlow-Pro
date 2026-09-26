import React from 'react';
import { BoardSkeleton } from '@/components/ui/Skeleton';

export default function LoadingProject() {
  return (
    <div className="space-y-6">
      <div className="h-16 w-1/3 bg-zinc-900 animate-pulse rounded-lg" />
      <BoardSkeleton />
    </div>
  );
}
