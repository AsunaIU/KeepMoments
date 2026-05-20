ALTER TABLE templates
    ADD COLUMN IF NOT EXISTS front_cover_mode TEXT NULL,
    ADD COLUMN IF NOT EXISTS front_cover_photo_id TEXT NULL,
    ADD COLUMN IF NOT EXISTS front_cover_text TEXT NULL,
    ADD COLUMN IF NOT EXISTS back_cover_mode TEXT NULL,
    ADD COLUMN IF NOT EXISTS back_cover_photo_id TEXT NULL,
    ADD COLUMN IF NOT EXISTS back_cover_text TEXT NULL;

ALTER TABLE template_slots
    ADD COLUMN IF NOT EXISTS required_orientation TEXT NULL;
