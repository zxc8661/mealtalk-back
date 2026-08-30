# Meal aggregate CRUD

This document records the completed meal stage only. It covers the shipped meal aggregate API, the immutable item snapshots, and the meal journal screen that consumes them; it does not describe earlier profile or food-management work beyond how meals reference existing foods.

## Authenticated API contract

Every meal operation acts on the bearer JWT's authenticated user. The client never supplies a user or owner ID.

| Method and endpoint | Success | Authentication failure | Purpose |
| --- | --- | --- | --- |
| `GET /api/v1/meals?date={date}` | `200 OK` | `401 Unauthorized` | Returns the authenticated user's meal journal for one date. |
| `GET /api/v1/meals/{mealId}` | `200 OK` | `401 Unauthorized` | Returns one meal owned by the authenticated user. |
| `POST /api/v1/meals` | `201 Created` | `401 Unauthorized` | Creates one meal with its complete item set. |
| `PUT /api/v1/meals/{mealId}` | `200 OK` | `401 Unauthorized` | Replaces an owned meal and its entire item set. |
| `DELETE /api/v1/meals/{mealId}` | `204 No Content` | `401 Unauthorized` | Deletes an owned meal and its items. |

`date` is required and must be an ISO calendar date; a non-date value returns `400 Bad Request`.

A single meal response is the same object returned by detail, create, and update:

```json
{
  "id": 1,
  "mealDate": "2026-08-29",
  "mealType": "LUNCH",
  "eatenAt": "2026-08-29T12:30:00Z",
  "items": [
    {
      "id": 10,
      "foodId": 5,
      "foodName": "Chicken breast",
      "amount": 150.0,
      "unit": "g",
      "caloriesKcal": 247.8,
      "carbohydratesG": 0.0,
      "proteinG": 46.8,
      "fatG": 5.4
    }
  ],
  "totalCaloriesKcal": 442.3,
  "totalCarbohydratesG": 33.15,
  "totalProteinG": 55.25,
  "totalFatG": 8.85
}
```

`GET /api/v1/meals?date=` returns a journal wrapping that same meal object:

```json
{
  "mealDate": "2026-08-29",
  "meals": [
    { "id": 1, "mealType": "BREAKFAST", "eatenAt": "2026-08-29T08:00:00Z", "items": [] },
    { "id": 2, "mealType": "LUNCH", "eatenAt": "2026-08-29T12:30:00Z", "items": [] },
    { "id": 3, "mealType": "DINNER", "eatenAt": null, "items": [] }
  ],
  "totalCaloriesKcal": 996.5,
  "totalCarbohydratesG": 99.45,
  "totalProteinG": 103.35,
  "totalFatG": 19.35
}
```

Each entry in `meals` is a complete meal object with its own `items` and totals, abbreviated above. `meals` can be empty; an empty day returns zero totals. `mealType` is one of `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`, `UNSPECIFIED`. `eatenAt` is an optional RFC 3339 instant and is absent when a meal has no recorded time.

## Aggregate-only items and full replacement

There is no standalone meal-item endpoint. Items exist only inside a meal and are always written through `POST` or `PUT` on the meal aggregate.

`PUT /api/v1/meals/{mealId}` is full replacement, not a patch: it updates the meal's own fields, deletes every existing item, and re-inserts the submitted items in one transaction. Replaced items receive new IDs; the prior item rows do not survive. `DELETE` removes the meal's items and then the meal.

### Create and replace input

Both create and update accept the complete desired meal:

```json
{
  "mealDate": "2026-08-29",
  "mealType": "LUNCH",
  "eatenAt": "2026-08-29T12:30:00Z",
  "items": [
    { "foodId": 5, "amount": 150 },
    { "foodId": 8, "amount": 50 }
  ]
}
```

Each item carries only `foodId` and `amount`. `mealDate` and `mealType` are required; `eatenAt` is optional. `items` is required, must contain at least one item, and at most 50. `amount` must be positive with at most seven integer digits and three fractional digits. Every `foodId` in a request must be unique; duplicates return `400 Bad Request`, as do an empty item list and malformed input.

Item name, unit, and nutrition are never accepted from the client. Any such fields in the payload are ignored: the server derives every snapshot from the referenced food, and the item response exposes no client-provided `name` field.

## Server-derived snapshots and totals

When a meal is written, the server captures an immutable snapshot per item from the referenced food:

- `foodName` and `unit` copy the food's current name and serving unit.
- Each macro is `foodNutrient * (amount / food.servingAmount)`. The ratio is computed at high precision and each stored nutrient is rounded to three decimals, half-up.
- `amount` is stored at three decimals.

Per-meal totals sum the item snapshots; the daily journal totals sum the meals. All totals are reported at three decimals. For example, 150 g of a 100 g / 165.2 kcal / 31.2 g-protein food yields `247.8 kcal` and `46.8 g` protein, a two-item lunch totals `442.3 / 33.15 / 55.25 / 8.85`, and a three-meal day totals `996.5 / 99.45 / 103.35 / 19.35`.

## Historical snapshot behavior

Item snapshots are historical records and do not change when the underlying food later changes. Renaming or archiving a food leaves existing meal items showing their original `foodName`, `unit`, and macros; only the linked food row reflects the later edit.

The `meal_items.food_name` snapshot column was added by the additive V3 migration, which backfills each row from its still-required `food_id -> foods.id` reference and then enforces `NOT NULL`. The unit and the four `NUMERIC(10, 3)` nutrient columns were already immutable snapshots in V1. Rows created before V3 had no stored historical name; their only truthful backfill source is the food name at migration time, so renames that happened before V3 cannot be reconstructed. Every name change after V3 is isolated by the snapshot.

## Ordering

Journal ordering is deterministic. Meals with an `eatenAt` come first, ordered by `eatenAt` ascending; meals without a time come last; ties break by ascending meal ID. Items within a meal are ordered by ascending item ID. The app mirrors this contract when normalizing cached or intercepted responses.

## Rejections, ownership, and errors

A referenced food must be active and owned by the authenticated user. An archived, foreign, or missing `foodId` returns `404 Not Found` on create and update; the meal is not written.

Meals are scoped to their owner. Detail, update, and delete for a meal that belongs to another user return `404 Not Found`, deliberately indistinguishable from a missing meal, and cannot mutate that meal.

Validation and malformed input return `400 Bad Request`; a non-date `date` query parameter returns `400`. Requests without a usable JWT return `401 Unauthorized` before the meal handler runs.

## Application flow and states

The authenticated meal tab consumes the meal API for one date at a time.

1. It loads the selected date's journal and shows loading, empty, and retryable load-error states. A failed refresh after content is shown keeps that content and marks it stale.
2. The daily totals panel shows the server-confirmed totals for the date. The editor preview shows a client-side estimate and states that the server confirms the final totals after saving.
3. Creating or editing a meal uses one aggregate save: pick meal type and optional time, search the user's active foods, add items with amounts, or create a new food inline that is added immediately. Editing an item separately is not offered.
4. Client validation requires at least one item and a positive amount with at most three decimals before a save is sent.
5. Save is pending only once; a network or `400` error keeps the full draft. A `404` reports that the meal or a selected food is no longer active and asks the user to refresh. A `401` clears the session and returns to login.
6. Delete requires a confirmation dialog that stays open on failure, respects reduced motion, and removes the meal from the day's totals on success.

Browser QA covered zero-total journals, exclusion of archived foods, invalid-quantity blocking, a two-item aggregate `POST`, a quantity edit via `PUT` with server totals, server-confirmed totals, a reduced-motion delete confirmation, delete reset, `404` editor retention, `401` sign-out, and no uncaught errors. The browser used HTTP interception at the typed REST boundary, not a live database.

## Verified evidence and commands

- Backend aggregate endpoints, server snapshots and totals, ordering, atomic replacement, deletion, validation, and owner isolation were verified through MockMvc and H2 integration tests: [task 8 evidence](../../.omo/evidence/task-8-staged-manual-crud.txt). The full suite reports 32 tests with 0 failures and 0 errors, including 3 `MealIntegrationTests` cases.
- Snapshot migration and rename/archive isolation coverage: [task 13 evidence](../../.omo/evidence/task-13-staged-manual-crud.txt).
- Meal journal and editor browser QA: [task 9 evidence](../../.omo/evidence/task-9-staged-manual-crud.txt) and [task 9 browser assertions](../../.omo/evidence/task-9-staged-manual-crud.browser.json).

The following commands are recorded as passing:

```sh
mealtalk-back/gradlew.bat -p mealtalk-back test --tests com.mealtalk.api.meal.MealIntegrationTests --rerun-tasks
mealtalk-back/gradlew.bat -p mealtalk-back test --rerun-tasks
git -C mealtalk-back diff --check
npm --prefix mealtalk-app run lint
cd mealtalk-app && npx tsc --noEmit -p tsconfig.json
git -C mealtalk-app diff --check
```

## Implementation references

- [Meal controller](../src/main/java/com/mealtalk/api/domain/meal/controller/MealController.java)
- [Meal service](../src/main/java/com/mealtalk/api/domain/meal/service/MealService.java)
- [Meal request](../src/main/java/com/mealtalk/api/domain/meal/dto/MealRequest.java)
- [Meal item request](../src/main/java/com/mealtalk/api/domain/meal/dto/MealItemRequest.java)
- [Meal response](../src/main/java/com/mealtalk/api/domain/meal/dto/MealResponse.java)
- [Meal item response](../src/main/java/com/mealtalk/api/domain/meal/dto/MealItemResponse.java)
- [Meal list response](../src/main/java/com/mealtalk/api/domain/meal/dto/MealListResponse.java)
- [Meal entity](../src/main/java/com/mealtalk/api/domain/meal/entity/Meal.java)
- [Meal item entity](../src/main/java/com/mealtalk/api/domain/meal/entity/MealItem.java)
- [Meal repository](../src/main/java/com/mealtalk/api/domain/meal/repository/MealRepository.java)
- [Meal item repository](../src/main/java/com/mealtalk/api/domain/meal/repository/MealItemRepository.java)
- [Meal history snapshot migration](../src/main/resources/db/migration/V3__add_meal_item_food_name_snapshot.sql)
- [Meal integration tests](../src/test/java/com/mealtalk/api/meal/MealIntegrationTests.java)
- [App meal API](../../mealtalk-app/src/meal/meal-api.ts)
- [App meal screen](../../mealtalk-app/src/meal/meal-screen.tsx)
