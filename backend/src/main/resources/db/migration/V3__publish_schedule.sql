-- When approved content goes live. One row, edited from the admin panel
-- (GET/PUT /admin/publish/schedule) and read by PublishScheduler on startup and on change.
--
-- cron is a Spring 6-field expression (sec min hour day month weekday), evaluated in
-- Asia/Kolkata. Default: every day at 05:00 IST, before people send their good-morning
-- messages. Enabled by default — it only ever publishes items a human already approved.

CREATE TABLE publish_schedule (
    id                SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),   -- singleton
    enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    cron              VARCHAR(64) NOT NULL DEFAULT '0 0 5 * * *',
    last_run_at       TIMESTAMPTZ,
    last_run_count    INT,                                                -- items published
    last_run_trigger  VARCHAR(16)                                         -- SCHEDULE | MANUAL
);

INSERT INTO publish_schedule (id) VALUES (1);
