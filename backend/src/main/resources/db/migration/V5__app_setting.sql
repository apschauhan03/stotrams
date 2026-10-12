-- Small key/value store for admin-editable text settings that don't deserve their own
-- table. First user: 'principle_prompt' — the admin's master instruction that an LLM
-- expands into many block prompts (PrincipleController).

CREATE TABLE app_setting (
    key        VARCHAR(64) PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
