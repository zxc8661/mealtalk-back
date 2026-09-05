-- Idempotency for meal creation. A double-tapped save must not produce two
-- records, so the client sends a UUID with the create and the server remembers
-- which record that UUID already produced.
--
-- fingerprint is a hash of the normalized payload. A retry of the same draft
-- replays the original record; the same UUID sent with a different payload is a
-- programming error on the client and is answered with 409 rather than silently
-- returning the wrong record.
--
-- The row is scoped by user_id so two clients can never collide on a UUID, and
-- it is deleted with its meal: once the record is gone there is nothing to
-- replay, and a retry is free to create a new record.
CREATE TABLE meal_write_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_request_id VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    meal_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_meal_write_requests_user_request UNIQUE (user_id, client_request_id),
    CONSTRAINT fk_meal_write_requests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_write_requests_meal FOREIGN KEY (meal_id) REFERENCES meals (id) ON DELETE CASCADE
);
