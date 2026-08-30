-- V1 foods have no ownership source to backfill safely. Adding user_id as nullable first
-- lets an empty V1 database upgrade, while SET NOT NULL deliberately rejects any
-- populated V1 foods instead of assigning them to an arbitrary user.
ALTER TABLE foods ADD COLUMN user_id BIGINT;
ALTER TABLE foods ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE foods ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE foods
    ADD CONSTRAINT fk_foods_user FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_foods_user_archived_normalized_name
    ON foods (user_id, archived, normalized_name);
