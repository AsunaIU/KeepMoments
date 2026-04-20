CREATE TABLE IF NOT EXISTS templates (
    id TEXT PRIMARY KEY,
    template_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
