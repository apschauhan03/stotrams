-- Prompt-based editing: an edit never changes the source image, it creates a new candidate.
-- These record where it came from and what was asked, so the review screen can say
-- "edit of #24: remove the text at the bottom".

ALTER TABLE content_item
    ADD COLUMN edited_from_id BIGINT REFERENCES content_item (id),
    ADD COLUMN edit_prompt    TEXT;
