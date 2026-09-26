-- V2__create_core_domain_tables.sql
-- Core domain tables: projects, tasks, and task_dependencies

-- 1. Projects table
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_project_name_not_empty CHECK (trim(name) <> '')
);

-- 2. Tasks table
CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    workflow_status VARCHAR(32) NOT NULL,
    dependency_status VARCHAR(32) NOT NULL,
    start_date DATE,
    due_date DATE,
    duration_days INTEGER,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT chk_task_title_not_empty CHECK (trim(title) <> ''),
    CONSTRAINT chk_task_workflow_status CHECK (workflow_status IN ('BACKLOG', 'IN_PROGRESS', 'REVIEW', 'DONE')),
    CONSTRAINT chk_task_dependency_status CHECK (dependency_status IN ('READY', 'BLOCKED')),
    CONSTRAINT chk_task_dates CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date),
    CONSTRAINT chk_task_duration CHECK (duration_days IS NULL OR duration_days >= 0)
);

CREATE INDEX idx_tasks_project_id ON tasks(project_id);

-- 3. Task Dependencies table (directed edge: predecessor -> successor)
CREATE TABLE task_dependencies (
    id UUID PRIMARY KEY,
    predecessor_task_id UUID NOT NULL,
    successor_task_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dep_predecessor FOREIGN KEY (predecessor_task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_successor FOREIGN KEY (successor_task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT uq_task_dependencies UNIQUE (predecessor_task_id, successor_task_id),
    CONSTRAINT chk_no_self_dependency CHECK (predecessor_task_id <> successor_task_id)
);

CREATE INDEX idx_task_dependencies_predecessor ON task_dependencies(predecessor_task_id);
CREATE INDEX idx_task_dependencies_successor ON task_dependencies(successor_task_id);
