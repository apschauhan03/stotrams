-- The day a block's images are meant for (e.g. a festival day), set when blocks are written
-- from the master prompt for a date. Shown on the block card. Null = not tied to a date.

ALTER TABLE prompt_template
    ADD COLUMN target_date DATE;
