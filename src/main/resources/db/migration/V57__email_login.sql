DELETE FROM users;

ALTER TABLE users RENAME COLUMN username TO email;
ALTER TABLE users DROP COLUMN password;

ALTER TABLE users
    ADD COLUMN login_code_hash       VARCHAR(64),
    ADD COLUMN login_code_expires_at TIMESTAMPTZ,
    ADD COLUMN login_code_sent_at    TIMESTAMPTZ,
    ADD COLUMN login_code_attempts   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN last_login_at         TIMESTAMPTZ;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
        CHECK (role IN ('ADMIN', 'SALES_MANAGER', 'PROJECT_MANAGER'));
