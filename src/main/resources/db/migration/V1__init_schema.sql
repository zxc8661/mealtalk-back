CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(100),
    profile_completed BOOLEAN NOT NULL,
    timezone VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_users_provider_subject UNIQUE (provider, provider_user_id)
);

CREATE TABLE user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    height_cm NUMERIC(5, 2) NOT NULL,
    weight_kg NUMERIC(5, 2) NOT NULL,
    activity_level VARCHAR(20) NOT NULL,
    goal_mode VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_profiles_height_positive CHECK (height_cm > 0),
    CONSTRAINT ck_user_profiles_weight_positive CHECK (weight_kg > 0)
);

CREATE TABLE user_targets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_value NUMERIC(10, 2) NOT NULL,
    due_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_targets_type UNIQUE (user_id, target_type),
    CONSTRAINT fk_user_targets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_targets_value_positive CHECK (target_value > 0)
);

CREATE TABLE chat_rooms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_chat_rooms_user UNIQUE (user_id),
    CONSTRAINT fk_chat_rooms_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    action VARCHAR(50),
    analysis_result JSONB,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_chat_messages_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id)
);

CREATE INDEX idx_chat_messages_room_created ON chat_messages (room_id, created_at);

CREATE TABLE foods (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    serving_amount NUMERIC(10, 3) NOT NULL,
    serving_unit VARCHAR(20) NOT NULL,
    calories_kcal NUMERIC(10, 3) NOT NULL,
    carbohydrates_g NUMERIC(10, 3) NOT NULL,
    protein_g NUMERIC(10, 3) NOT NULL,
    fat_g NUMERIC(10, 3) NOT NULL,
    external_source VARCHAR(50),
    external_food_id VARCHAR(200),
    last_fetched_at DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_foods_serving_amount_positive CHECK (serving_amount > 0),
    CONSTRAINT ck_foods_calories_non_negative CHECK (calories_kcal >= 0),
    CONSTRAINT ck_foods_carbohydrates_non_negative CHECK (carbohydrates_g >= 0),
    CONSTRAINT ck_foods_protein_non_negative CHECK (protein_g >= 0),
    CONSTRAINT ck_foods_fat_non_negative CHECK (fat_g >= 0)
);

CREATE INDEX idx_foods_normalized_name ON foods (normalized_name);
CREATE UNIQUE INDEX uk_foods_external_id ON foods (external_source, external_food_id)
WHERE external_source IS NOT NULL AND external_food_id IS NOT NULL;

CREATE TABLE meals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    meal_date DATE NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    eaten_at TIMESTAMPTZ,
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_meals_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_meals_user_date_eaten ON meals (user_id, meal_date, eaten_at);

CREATE TABLE meal_items (
    id BIGSERIAL PRIMARY KEY,
    meal_id BIGINT NOT NULL,
    food_id BIGINT NOT NULL,
    amount NUMERIC(10, 3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    calories_kcal NUMERIC(10, 3) NOT NULL,
    carbohydrates_g NUMERIC(10, 3) NOT NULL,
    protein_g NUMERIC(10, 3) NOT NULL,
    fat_g NUMERIC(10, 3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_meal_items_meal FOREIGN KEY (meal_id) REFERENCES meals (id),
    CONSTRAINT fk_meal_items_food FOREIGN KEY (food_id) REFERENCES foods (id),
    CONSTRAINT ck_meal_items_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_meal_items_calories_non_negative CHECK (calories_kcal >= 0),
    CONSTRAINT ck_meal_items_carbohydrates_non_negative CHECK (carbohydrates_g >= 0),
    CONSTRAINT ck_meal_items_protein_non_negative CHECK (protein_g >= 0),
    CONSTRAINT ck_meal_items_fat_non_negative CHECK (fat_g >= 0)
);
