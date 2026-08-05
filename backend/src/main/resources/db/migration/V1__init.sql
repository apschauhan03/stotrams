-- Stotram initial schema. Five tables; content_item is the heart.
-- See README section 3 (Data model).

CREATE TABLE admin_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,        -- bcrypt hash, never plaintext
    last_login_at TIMESTAMPTZ
);

CREATE TABLE category (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug       VARCHAR(64)  NOT NULL UNIQUE,     -- e.g. good-morning, good-night
    name_en    VARCHAR(128) NOT NULL,
    name_hi    VARCHAR(128) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE prompt_template (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id   BIGINT  NOT NULL REFERENCES category (id),
    template_text TEXT    NOT NULL,
    model         VARCHAR(128),                  -- which image model this prompt targets
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE generation_batch (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prompt_template_id BIGINT REFERENCES prompt_template (id),
    admin_user_id      BIGINT REFERENCES admin_user (id),
    requested_count    INT           NOT NULL,
    cost_usd           NUMERIC(10, 4),           -- per-batch cost, visible from day one
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE content_item (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id     BIGINT REFERENCES generation_batch (id),   -- null for seeded/manual items
    category_id  BIGINT      NOT NULL REFERENCES category (id),
    type         VARCHAR(16) NOT NULL DEFAULT 'IMAGE',       -- IMAGE (later VIDEO)
    status       VARCHAR(16) NOT NULL DEFAULT 'GENERATED',   -- GENERATED->APPROVED->PUBLISHED / REJECTED
    language     VARCHAR(8)  NOT NULL,                       -- hi, en, ...
    r2_key       VARCHAR(512) NOT NULL UNIQUE,               -- object key; public url = <base>/<r2_key>
    width        INT,
    height       INT,
    publish_date DATE,                                       -- which day's feed this belongs to
    published_at TIMESTAMPTZ
);

-- Feed query: published items for a (category, language), newest first.
CREATE INDEX idx_content_item_feed
    ON content_item (status, category_id, language, publish_date DESC, id DESC);
