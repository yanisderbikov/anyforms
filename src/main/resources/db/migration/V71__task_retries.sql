ALTER TABLE task
    ADD COLUMN attempts        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX idx_task_type_status_created_at ON task (type, status, created_at);
