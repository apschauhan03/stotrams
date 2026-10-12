-- Publishing now writes a watermarked JPEG as a new object and points r2_key at it.
-- original_key keeps the clean, as-generated image so the watermark can always be
-- re-rendered from it (republishing, or a changed watermark design). NULL = never processed.

ALTER TABLE content_item ADD COLUMN original_key VARCHAR(512);
