'use client';

import React from 'react';
import { KanbanWorkspace } from '@/components/board/KanbanWorkspace';

interface ProjectPageProps {
  params: {
    projectId: string;
  };
}

export default function ProjectPage({ params }: ProjectPageProps) {
  return <KanbanWorkspace initialProjectId={params.projectId} />;
}
