-- V4__seed_demo_data.sql
-- Seed deterministic demo project for TaskFlow Pro workflow, DAG scheduling, and critical path demonstration

-- 1. Demo Project
INSERT INTO projects (id, name, description, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Payment Checkout Integration',
    'Production workflow demonstrating deterministic DAG scheduling, non-compounding converging paths, and critical path bottlenecks.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 2. Demo Tasks
-- Task A: Design Payment Schema (DONE, READY)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000011',
    '00000000-0000-0000-0000-000000000001',
    'Design Payment Schema',
    'Design PostgreSQL schema, indexes, and idempotency key constraints for transactions.',
    'DONE', 'READY',
    '2026-06-01', '2026-06-03', '2026-06-01', '2026-06-01', '2026-06-03', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task B: Implement Payment API (IN_PROGRESS, READY)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000012',
    '00000000-0000-0000-0000-000000000001',
    'Implement Payment API',
    'Build idempotent REST endpoints for checkout authorization and webhook handling.',
    'IN_PROGRESS', 'READY',
    '2026-06-04', '2026-06-08', '2026-06-04', '2026-06-04', '2026-06-08', 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task C: Build Checkout UI (BACKLOG, READY)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000013',
    '00000000-0000-0000-0000-000000000001',
    'Build Checkout UI',
    'Responsive checkout component with card validation and error handling states.',
    'BACKLOG', 'READY',
    '2026-06-04', '2026-06-07', '2026-06-04', '2026-06-04', '2026-06-07', 4, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task D: Integrate Payment Provider (BACKLOG, BLOCKED by B and C)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000014',
    '00000000-0000-0000-0000-000000000001',
    'Integrate Payment Provider',
    'End-to-end integration connecting checkout UI and API to external gateway provider.',
    'BACKLOG', 'BLOCKED',
    '2026-06-09', '2026-06-12', '2026-06-09', '2026-06-09', '2026-06-12', 4, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task E: Write Integration Tests (BACKLOG, BLOCKED by D)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000015',
    '00000000-0000-0000-0000-000000000001',
    'Write Integration Tests',
    'Automated integration tests validating payment reconciliation and webhook retries.',
    'BACKLOG', 'BLOCKED',
    '2026-06-13', '2026-06-15', '2026-06-13', '2026-06-13', '2026-06-15', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task F: Security & Compliance Review (BACKLOG, BLOCKED by B)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000016',
    '00000000-0000-0000-0000-000000000001',
    'Security & Compliance Review',
    'PCI-DSS compliance audit and secret handling validation for payment credentials.',
    'BACKLOG', 'BLOCKED',
    '2026-06-09', '2026-06-11', '2026-06-09', '2026-06-09', '2026-06-11', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task G: Staging Deployment & Verification (BACKLOG, BLOCKED by E and F)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000017',
    '00000000-0000-0000-0000-000000000001',
    'Staging Deployment & Verification',
    'Deploy to staging environment and perform sandbox end-to-end payment run.',
    'BACKLOG', 'BLOCKED',
    '2026-06-16', '2026-06-17', '2026-06-16', '2026-06-16', '2026-06-17', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Task H: Production Rollout (BACKLOG, BLOCKED by G)
INSERT INTO tasks (
    id, project_id, title, description, workflow_status, dependency_status,
    start_date, due_date, planned_start_date, scheduled_start_date, scheduled_due_date, duration_days, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000018',
    '00000000-0000-0000-0000-000000000001',
    'Production Rollout',
    'Canary rollout to production traffic with real-time error rate monitoring.',
    'BACKLOG', 'BLOCKED',
    '2026-06-18', '2026-06-18', '2026-06-18', '2026-06-18', '2026-06-18', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 3. Demo Dependencies (Directed Edges: Predecessor -> Successor)
-- A -> B
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000012', CURRENT_TIMESTAMP);

-- A -> C
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000013', CURRENT_TIMESTAMP);

-- B -> D (Converging path 1)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000014', CURRENT_TIMESTAMP);

-- C -> D (Converging path 2)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000014', CURRENT_TIMESTAMP);

-- B -> F (Parallel branch)
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000016', CURRENT_TIMESTAMP);

-- D -> E
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000106', '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000015', CURRENT_TIMESTAMP);

-- E -> G
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000107', '00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000017', CURRENT_TIMESTAMP);

-- F -> G
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000108', '00000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000017', CURRENT_TIMESTAMP);

-- G -> H
INSERT INTO task_dependencies (id, predecessor_task_id, successor_task_id, created_at)
VALUES ('00000000-0000-0000-0000-000000000109', '00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000018', CURRENT_TIMESTAMP);
