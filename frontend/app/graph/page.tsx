'use client';

import React from 'react';
import { KanbanWorkspace } from '@/components/board/KanbanWorkspace';

export default function GraphPage() {
  return <KanbanWorkspace initialView="GRAPH" />;
}
