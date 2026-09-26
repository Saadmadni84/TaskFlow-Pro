-- V3__add_task_schedule_baseline.sql
-- Add baseline planned and calculated scheduled dates to tasks table for Phase 5 scheduling engine

ALTER TABLE tasks ADD COLUMN planned_start_date DATE;
ALTER TABLE tasks ADD COLUMN scheduled_start_date DATE;
ALTER TABLE tasks ADD COLUMN scheduled_due_date DATE;

-- Initialize planned and scheduled dates from existing start_date and due_date
UPDATE tasks
SET planned_start_date = start_date,
    scheduled_start_date = start_date,
    scheduled_due_date = due_date
WHERE start_date IS NOT NULL;

-- Compute duration_days if not already populated (default to 1 for scheduled tasks)
UPDATE tasks
SET duration_days = 1
WHERE duration_days IS NULL AND start_date IS NOT NULL;
