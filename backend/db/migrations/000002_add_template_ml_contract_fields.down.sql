ALTER TABLE template_slots
    DROP COLUMN IF EXISTS required_orientation;

ALTER TABLE templates
    DROP COLUMN IF EXISTS back_cover_text,
    DROP COLUMN IF EXISTS back_cover_photo_id,
    DROP COLUMN IF EXISTS back_cover_mode,
    DROP COLUMN IF EXISTS front_cover_text,
    DROP COLUMN IF EXISTS front_cover_photo_id,
    DROP COLUMN IF EXISTS front_cover_mode;
