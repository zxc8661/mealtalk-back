-- A meal record is now a photo and/or a memo instead of a list of nutrition items.
-- meals.source_text becomes the user-authored memo exposed by the API as `memo`.
--
-- Historic rows were written by the nutrition-item flow and left source_text NULL,
-- so they would violate the new "memo or photo" record invariant. Backfill them from
-- their own ordered meal_items name snapshots, which is deterministic and needs no
-- join back to foods (the snapshot is already immutable since V3). foods and
-- meal_items themselves are read-only here: nothing is deleted or altered.
UPDATE meals
SET source_text = COALESCE(
    (
        SELECT LEFT(string_agg(item.food_name, ', ' ORDER BY item.id), 1000)
        FROM meal_items item
        WHERE item.meal_id = meals.id
    ),
    '기록 내용 없음'
)
WHERE source_text IS NULL OR TRIM(source_text) = '';

-- One current photo per meal. The owner is derived through meals.user_id, so no
-- user_id is duplicated here. object_key is an opaque private-storage key and is
-- never returned by the API; bytes are always served through an authenticated path.
CREATE TABLE meal_photos (
    id BIGSERIAL PRIMARY KEY,
    meal_id BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_meal_photos_meal UNIQUE (meal_id),
    CONSTRAINT fk_meal_photos_meal FOREIGN KEY (meal_id) REFERENCES meals (id) ON DELETE CASCADE,
    CONSTRAINT ck_meal_photos_byte_size_positive CHECK (byte_size > 0),
    CONSTRAINT ck_meal_photos_width_positive CHECK (width > 0),
    CONSTRAINT ck_meal_photos_height_positive CHECK (height > 0)
);
