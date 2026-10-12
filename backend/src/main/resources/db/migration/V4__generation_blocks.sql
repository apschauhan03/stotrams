-- Generation blocks: each prompt_template row becomes a "block" managed from the admin
-- panel (/admin/blocks) — a named prompt with its own category, language, image model and
-- how many images one run makes. A category can now have any number of blocks.
--
-- The table keeps its name (generation_batch.prompt_template_id points at it); "block" is
-- the admin-facing word for a prompt_template row.

ALTER TABLE prompt_template
    ADD COLUMN name        VARCHAR(128),
    ADD COLUMN language    VARCHAR(8)  NOT NULL DEFAULT 'hi',
    ADD COLUMN image_count INT         NOT NULL DEFAULT 10 CHECK (image_count BETWEEN 1 AND 50),
    ADD COLUMN sort_order  INT         NOT NULL DEFAULT 0,
    -- Deleting a block archives it: past batches still reference it for cost history.
    ADD COLUMN archived    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at  TIMESTAMPTZ NOT NULL DEFAULT now();

-- Name the existing prompts after their category ("Good Morning", "Good Night").
UPDATE prompt_template p
SET name = c.name_en,
    sort_order = p.id
FROM category c
WHERE c.id = p.category_id;

ALTER TABLE prompt_template ALTER COLUMN name SET NOT NULL;

-- Which block a run used, newest first (for "last generated" in the admin panel).
CREATE INDEX idx_generation_batch_template ON generation_batch (prompt_template_id, created_at DESC);
