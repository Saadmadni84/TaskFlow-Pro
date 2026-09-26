-- V1__init.sql: TaskFlow Pro initial database migration baseline
-- Domain tables (tasks, dependencies, execution schedules) will be introduced in subsequent phases.

CREATE TABLE IF NOT EXISTS schema_baseline (
    id VARCHAR(64) PRIMARY KEY,
    initialized_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255) NOT NULL
);

INSERT INTO schema_baseline (id, description)
VALUES ('phase-1-baseline', 'TaskFlow Pro Phase 1 foundation established');
