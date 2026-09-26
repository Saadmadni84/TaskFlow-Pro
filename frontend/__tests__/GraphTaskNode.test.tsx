import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { GraphTaskNode } from '@/components/graph/GraphTaskNode';
import { Position, ReactFlowProvider } from '@xyflow/react';

describe('GraphTaskNode', () => {
  const baseTask = {
    id: 'task-1',
    title: 'Design Architecture',
    workflowStatus: 'IN_PROGRESS' as const,
    dependencyStatus: 'READY' as const,
    scheduledStartDate: '2026-06-01',
    scheduledDueDate: '2026-06-05',
    durationDays: 5,
  };

  it('renders task title, workflow status, readiness, and schedule dates', () => {
    render(
      <ReactFlowProvider>
        <GraphTaskNode
          id="task-1"
          data={{
            task: baseTask,
            isSelected: false,
            isPredecessor: false,
            isSuccessor: false,
            isAncestor: false,
            isDescendant: false,
          }}
          type="taskNode"
          selected={false}
          zIndex={0}
          isConnectable={true}
          positionAbsoluteX={0}
          positionAbsoluteY={0}
          dragging={false}
          deletable={false}
          selectable={true}
          draggable={true}
          sourcePosition={Position.Right}
          targetPosition={Position.Left}
        />
      </ReactFlowProvider>
    );

    expect(screen.getByText('Design Architecture')).toBeInTheDocument();
    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument();
    expect(screen.getByText('READY')).toBeInTheDocument();
    expect(screen.getByText('5d')).toBeInTheDocument();
    expect(screen.getByText('2026-06-01')).toBeInTheDocument();
    expect(screen.getByText('2026-06-05')).toBeInTheDocument();
  });

  it('renders prerequisite badge when task is a prerequisite of selected node', () => {
    render(
      <ReactFlowProvider>
        <GraphTaskNode
          id="task-1"
          data={{
            task: baseTask,
            isSelected: false,
            isPredecessor: true,
            isSuccessor: false,
            isAncestor: false,
            isDescendant: false,
          }}
          type="taskNode"
          selected={false}
          zIndex={0}
          isConnectable={true}
          positionAbsoluteX={0}
          positionAbsoluteY={0}
          dragging={false}
          deletable={false}
          selectable={true}
          draggable={true}
          sourcePosition={Position.Right}
          targetPosition={Position.Left}
        />
      </ReactFlowProvider>
    );

    expect(screen.getByText('Prerequisite')).toBeInTheDocument();
  });

  it('renders dependent badge when task is dependent on selected node', () => {
    render(
      <ReactFlowProvider>
        <GraphTaskNode
          id="task-1"
          data={{
            task: baseTask,
            isSelected: false,
            isPredecessor: false,
            isSuccessor: true,
            isAncestor: false,
            isDescendant: false,
          }}
          type="taskNode"
          selected={false}
          zIndex={0}
          isConnectable={true}
          positionAbsoluteX={0}
          positionAbsoluteY={0}
          dragging={false}
          deletable={false}
          selectable={true}
          draggable={true}
          sourcePosition={Position.Right}
          targetPosition={Position.Left}
        />
      </ReactFlowProvider>
    );

    expect(screen.getByText('Dependent')).toBeInTheDocument();
  });
});
