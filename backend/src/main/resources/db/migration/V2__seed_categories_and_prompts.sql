-- Seeds the two launch categories and one generation prompt each.
--
-- Why this exists: categories used to be created on boot by InitialContentLoader, which was
-- removed. Nothing else creates them, and POST /admin/generate cannot run without a category
-- and an active prompt_template to read a prompt from. Flyway is the right owner for this —
-- it is schema-adjacent reference data, not user content, and it belongs in version control.
--
-- ON CONFLICT guards the case where a database already carries these rows from the old loader.

INSERT INTO category (slug, name_en, name_hi, sort_order, active) VALUES
    ('good-morning', 'Good Morning', 'सुप्रभात',   1, TRUE),
    ('good-night',   'Good Night',   'शुभ रात्रि', 2, TRUE)
ON CONFLICT (slug) DO NOTHING;

-- One active prompt per category. These are editable from the admin panel later; the text
-- here is a starting point, not a fixture.
--
-- `model` is `provider:model-id`, split on the FIRST colon (vendor model ids contain their
-- own colons). 'fake' selects FakeImageGenerator, which draws a local placeholder and costs
-- nothing — so the pipeline is runnable before any vendor credentials exist.
-- To switch a category to a real provider, update this column, e.g.:
--     UPDATE prompt_template SET model = 'bedrock:amazon.nova-canvas-v1:0' WHERE ...;
INSERT INTO prompt_template (category_id, template_text, model, active)
SELECT c.id,
       'A serene Indian devotional good-morning scene at sunrise: warm golden light over a '
       || 'temple, marigold garlands, brass diya lamps, lotus flowers, soft mist, intricate '
       || 'traditional detailing, vertical composition, no text, no watermark.',
       'fake',
       TRUE
FROM category c
WHERE c.slug = 'good-morning'
  AND NOT EXISTS (SELECT 1 FROM prompt_template p WHERE p.category_id = c.id);

INSERT INTO prompt_template (category_id, template_text, model, active)
SELECT c.id,
       'A peaceful Indian devotional good-night scene: moonlit temple silhouette, deep indigo '
       || 'night sky, glowing oil lamps, jasmine flowers, still water reflections, calm and '
       || 'restful mood, intricate traditional detailing, vertical composition, no text, no watermark.',
       'fake',
       TRUE
FROM category c
WHERE c.slug = 'good-night'
  AND NOT EXISTS (SELECT 1 FROM prompt_template p WHERE p.category_id = c.id);
