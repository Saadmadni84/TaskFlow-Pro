-- V5__seed_realistic_showcase_demo.sql
-- Seed the realistic showcase demo project for TaskFlow Pro

INSERT INTO projects (id, name, description, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'TaskFlow Pro Core Platform',
    'Flagship multi-tier DAG demonstration featuring converging non-compounding paths, positive slack, critical path bottlenecks, and independent tasks.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Task 1: Requirements & API Contract Analysis (DONE, READY)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000011',
    'a0000000-0000-0000-0000-000000000001',
    'Requirements & API Contract Analysis',
    'Define OpenAPI specifications, contract boundaries, and schema invariants.',
    'DONE', 'READY',
    '2026-06-01', '2026-06-03', '2026-06-01', '2026-06-01', '2026-06-03', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task 2: Database Schema & Migration Design (DONE, READY)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000012',
    'a0000000-0000-0000-0000-000000000001',
    'Database Schema & Migration Design',
    'Design PostgreSQL relational tables, foreign key constraints, and Flyway migrations.',
    'DONE', 'READY',
    '2026-06-04', '2026-06-06', '2026-06-04', '2026-06-04', '2026-06-06', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task 3: Backend API & Core Engine Development (IN_PROGRESS, READY) - Critical branch (5d)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000013',
    'a0000000-0000-0000-0000-000000000001',
    'Backend API & Core Engine Development',
    'Implement REST endpoints, Kahn topological sorting, cycle detection, and scheduling propagation.',
    'IN_PROGRESS', 'READY',
    '2026-06-07', '2026-06-11', '2026-06-07', '2026-06-07', '2026-06-11', 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task 4: Frontend Dashboard & Visual DAG Canvas (IN_PROGRESS, READY) - Shorter branch (3d, Float = 2d)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000014',
    'a0000000-0000-0000-0000-000000000001',
    'Frontend Dashboard & Visual DAG Canvas',
    'Build interactive Next.js Kanban board, ReactFlow visual DAG canvas, and CPM inspector.',
    'IN_PROGRESS', 'READY',
    '2026-06-07', '2026-06-09', '2026-06-07', '2026-06-07', '2026-06-09', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task 5: Dependency Engine Integration & Converging Validation (BACKLOG, BLOCKED by 3 and 4)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000015',
    'a0000000-0000-0000-0000-000000000001',
    'Dependency Engine Integration & Converging Validation',
    'End-to-end integration connecting frontend canvas to backend DAG engine without compounding converging delays.',
    'BACKLOG', 'BLOCKED',
    '2026-06-12', '2026-06-15', '2026-06-12', '2026-06-12', '2026-06-15', 4, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task 6: Automated Integration & E2E Testing (BACKLOG, BLOCKED by 5) - Critical branch (3d)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000016',
    'a0000000-0000-0000-0000-000000000001',
    'Automated Integration & E2E Testing',
    'Comprehensive automated test suite covering schedule propagation, readiness rollback, and cycle prevention.',
    'BACKLOG', 'BLOCKED',
    '2026-06-16', '2026-06-18', '2026-06-16', '2026-06-16', '2026-06-18', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task 7: Performance & Security Audit (BACKLOG, BLOCKED by 5) - Shorter branch (2d, Float = 1d)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000017',
    'a0000000-0000-0000-0000-000000000001',
    'Performance & Security Audit',
    'O(V+E) algorithm performance benchmarks, input sanitization, and authorization boundary validation.',
    'BACKLOG', 'BLOCKED',
    '2026-06-16', '2026-06-17', '2026-06-16', '2026-06-16', '2026-06-17', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task 8: Production Staging Deployment (BACKLOG, BLOCKED by 6 and 7) - Terminal Task
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000018',
    'a0000000-0000-0000-0000-000000000001',
    'Production Staging Deployment',
    'Deploy to production-identical staging cluster and verify live schedule recalculation.',
    'BACKLOG', 'BLOCKED',
    '2026-06-19', '2026-06-20', '2026-06-19', '2026-06-19', '2026-06-20', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task 9: Disaster Recovery Runbook & Operations Manual (BACKLOG, READY) - Independent Root Task!
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000019',
    'a0000000-0000-0000-0000-000000000001',
    'Disaster Recovery Runbook & Operations Manual',
    'Independent operational documentation for database backups, failover procedures, and system runbooks.',
    'BACKLOG', 'READY',
    '2026-06-01', '2026-06-05', '2026-06-01', '2026-06-01', '2026-06-05', 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Dependencies: Directed Edges (Predecessor -> Successor)
-- 1 -> 2
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000101', 'a0000000-0000-0000-0000-000000000011', 'a0000000-0000-0000-0000-000000000012', CURRENT_TIMESTAMP);

-- 2 -> 3 (Branch 1 - Critical)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000102', 'a0000000-0000-0000-0000-000000000012', 'a0000000-0000-0000-0000-000000000013', CURRENT_TIMESTAMP);

-- 2 -> 4 (Branch 2 - Float)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000103', 'a0000000-0000-0000-0000-000000000012', 'a0000000-0000-0000-0000-000000000014', CURRENT_TIMESTAMP);

-- 3 -> 5 (Converging Inbound 1)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000104', 'a0000000-0000-0000-0000-000000000013', 'a0000000-0000-0000-0000-000000000015', CURRENT_TIMESTAMP);

-- 4 -> 5 (Converging Inbound 2)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000105', 'a0000000-0000-0000-0000-000000000014', 'a0000000-0000-0000-0000-000000000015', CURRENT_TIMESTAMP);

-- 5 -> 6 (Branch 1 - Critical)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000106', 'a0000000-0000-0000-0000-000000000015', 'a0000000-0000-0000-0000-000000000016', CURRENT_TIMESTAMP);

-- 5 -> 7 (Branch 2 - Float)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000107', 'a0000000-0000-0000-0000-000000000015', 'a0000000-0000-0000-0000-000000000017', CURRENT_TIMESTAMP);

-- 6 -> 8 (Converging Inbound 1)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000108', 'a0000000-0000-0000-0000-000000000016', 'a0000000-0000-0000-0000-000000000018', CURRENT_TIMESTAMP);

-- 7 -> 8 (Converging Inbound 2)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('a0000000-0000-0000-0000-000000000109', 'a0000000-0000-0000-0000-000000000017', 'a0000000-0000-0000-0000-000000000018', CURRENT_TIMESTAMP);
