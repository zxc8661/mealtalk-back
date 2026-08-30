-- Meal item units and nutrient values were already immutable snapshots in V1.
-- Backfill the missing food-name snapshot from each still-required food reference before
-- enforcing the same non-null historical-record contract.
ALTER TABLE meal_items ADD COLUMN food_name VARCHAR(200);

UPDATE meal_items
SET food_name = (
    SELECT name
    FROM foods
    WHERE foods.id = meal_items.food_id
);

ALTER TABLE meal_items ALTER COLUMN food_name SET NOT NULL;
