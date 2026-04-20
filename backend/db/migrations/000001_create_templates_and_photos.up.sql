CREATE TABLE IF NOT EXISTS templates (
    id TEXT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS template_pages (
    template_id TEXT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    id TEXT NOT NULL,
    page_order INTEGER NOT NULL,
    PRIMARY KEY (template_id, id)
);

CREATE TABLE IF NOT EXISTS template_slots (
    template_id TEXT NOT NULL,
    page_id TEXT NOT NULL,
    id TEXT NOT NULL,
    photo_id TEXT NULL,
    slot_order INTEGER NOT NULL,
    PRIMARY KEY (template_id, page_id, id),
    FOREIGN KEY (template_id, page_id) REFERENCES template_pages(template_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS photos (
    id BIGSERIAL PRIMARY KEY,
    template_id TEXT NOT NULL REFERENCES templates(id) ON DELETE RESTRICT,
    file_name TEXT NOT NULL,
    content_type TEXT NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    description_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
