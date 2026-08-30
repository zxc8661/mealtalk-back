# Private custom-food CRUD

This document records the completed private-food stage only. It covers the shipped food migration, authenticated API, and food-management screen; it does not describe meal work.

## Authenticated API contract

All food operations use the bearer JWT's authenticated user. The client never supplies an owner ID.

| Method and endpoint | Success | Authentication failure | Purpose |
| --- | --- | --- | --- |
| `GET /api/v1/foods` | `200 OK` | `401 Unauthorized` | Lists the authenticated user's active foods. |
| `GET /api/v1/foods?query={query}` | `200 OK` | `401 Unauthorized` | Searches that user's active foods by a partial name. |
| `GET /api/v1/foods/{foodId}` | `200 OK` | `401 Unauthorized` | Returns one active food owned by the authenticated user. |
| `POST /api/v1/foods` | `201 Created` | `401 Unauthorized` | Creates a private active food. |
| `PUT /api/v1/foods/{foodId}` | `200 OK` | `401 Unauthorized` | Fully replaces the editable fields of an active owned food. |
| `DELETE /api/v1/foods/{foodId}` | `204 No Content` | `401 Unauthorized` | Archives an active owned food. |

A list response is an array of the same food objects returned by create, detail, and update. The response contains only:

```json
{
  "id": 1,
  "name": "Chicken breast",
  "servingAmount": 100.0,
  "servingUnit": "g",
  "caloriesKcal": 165.2,
  "carbohydratesG": 0,
  "proteinG": 31.2,
  "fatG": 3.6
}
```

`query` is optional. The server trims and lowercases it before matching the stored normalized name; omitted or blank queries list all active foods for that owner. Results are ordered by name.

## Ownership, visibility, and archiving

Every food has a non-null `user_id`. Lists and single-food lookups are scoped to that owner and exclude `archived` rows. A foreign ID is deliberately indistinguishable from an absent ID: detail, update, and archive return `404 Not Found`. A different user sees an empty list rather than another user's food and cannot mutate it.

`DELETE` is archive-not-delete: it sets the food's archive state and returns `204`; the database row remains. Archived foods disappear from list/search and behave as `404` for later detail, update, or archive requests, including for their owner.

Archive preserves the food row and existing foreign-key references, rather than removing a food that historical records can reference. This stage does not add meal-history name snapshots or meal APIs; immutable meal-history work remains outside this document.

## Create and full-update input

Create and update accept exactly these editable fields:

```json
{
  "name": "Chicken breast",
  "servingAmount": 100.0,
  "servingUnit": "g",
  "caloriesKcal": 165.2,
  "carbohydratesG": 0,
  "proteinG": 31.2,
  "fatG": 3.6
}
```

All fields are required. `name` must be non-blank, trimmed, and at most 200 characters; `servingUnit` must be non-blank, trimmed, and at most 20 characters. `servingAmount` must be positive. Each nutrition value must be non-negative. Every numeric field permits at most three fractional decimal places (and up to seven integer digits).

Malformed JSON and invalid input return `400 Bad Request`, including surrounding whitespace in either text field, over-precision numeric values, zero serving amount, and negative nutrition values. An absent, foreign, or archived food ID returns `404 Not Found`. Requests without a usable JWT, including a malformed bearer token, return `401 Unauthorized`.

Ownership and server-managed data are not editable. Client attempts to provide `userId`, `normalizedName`, `externalSource`, `externalFoodId`, `lastFetchedAt`, or macro-total fields cannot change persisted ownership or metadata and those fields are absent from the response. The server derives the normalized name from the supplied name.

## Migration and upgrade caveats

`V2__add_private_food_ownership.sql` is additive: it adds `foods.user_id`, adds `foods.archived` with a `FALSE` default, then requires a non-null owner, adds the user foreign key, and adds the `(user_id, archived, normalized_name)` index.

V1 foods have no safe ownership source. Therefore an empty V1-shaped database can upgrade, but a V1 database containing a food with no owner is intentionally rejected when V2 makes `user_id` non-null; the migration does not assign legacy foods to an arbitrary user. The original V1 migration is unchanged. Fresh V1-to-V2 and empty-V1 upgrade paths were verified, along with rejection of an unsafe populated-V1 backfill.

## Application flow and states

The authenticated `내 식품` tab at `/explore` consumes the private-food API.

1. It loads the active list and shows loading, empty, and retryable load-error states. A failed refresh after content is already shown retains that content and marks it stale.
2. Search submits a trimmed partial name. The user can return from a search to the full active list.
3. Create and edit use the seven editable API fields only. Local validation requires names and units, a positive serving amount, non-negative nutrition values, and no more than three fractional decimal places.
4. Save is pending only once; editor controls and other-food actions are disabled while it is in flight. A successful create or full update updates the displayed list and provides feedback.
5. A `400` save error keeps the draft and presents error feedback. Network errors also preserve the complete draft. A `404` update shows stale-resource feedback and keeps the draft for the user to review.
6. Archive requires confirmation. An archive failure leaves the confirmation open; a successful archive removes the food from the active list/search and confirms that it was archived. A `404` archive reports that the food is already unavailable.
7. The shared API client handles `401` by clearing the persisted session and returning to login.

Browser QA covered the list-load retry, validation, draft retention, create/search/edit, stale `404`, archive failure and success, retained stale list, `401` sign-out, reduced-motion archive dialog, pending-save interruption handling, and compact/light plus wide/dark layouts. The browser used Playwright HTTP interception at the typed REST boundary, not a live database.

## Verified evidence and commands

- Migration fresh/upgrade/rejection coverage: [task 2 evidence](../../.omo/evidence/task-2-staged-manual-crud.txt).
- Owner CRUD, archive persistence, validation, `401`/`404`, and untrusted-field coverage: [task 5 evidence](../../.omo/evidence/task-5-staged-manual-crud.txt).
- Food UI browser assertions and artifacts: [task 7 browser assertions](../../.omo/evidence/task-7-staged-manual-crud.browser.txt), [task 7 evidence](../../.omo/evidence/task-7-staged-manual-crud.txt), [happy-path screenshot](../../.omo/evidence/task-7-staged-manual-crud.happy.png), and [failure-state screenshot](../../.omo/evidence/task-7-staged-manual-crud.failure.png).
- The execution ledger records the independently verified results: [ledger](../../.omo/start-work/ledger.jsonl).

The following commands are recorded as passing:

```sh
mealtalk-back/gradlew.bat -p mealtalk-back test --tests com.mealtalk.api.domain.FoodOwnershipMigrationTests --rerun-tasks
mealtalk-back/gradlew.bat -p mealtalk-back test --rerun-tasks
cd mealtalk-back && git diff --check
npm --prefix mealtalk-app run lint
cd mealtalk-app && npm exec tsc -- --noEmit -p tsconfig.json
git -C mealtalk-app diff --check
```

## Implementation references

- [Food ownership migration](../src/main/resources/db/migration/V2__add_private_food_ownership.sql)
- [Food controller](../src/main/java/com/mealtalk/api/domain/food/controller/FoodController.java)
- [Food request](../src/main/java/com/mealtalk/api/domain/food/dto/FoodRequest.java)
- [Food response](../src/main/java/com/mealtalk/api/domain/food/dto/FoodResponse.java)
- [Food service](../src/main/java/com/mealtalk/api/domain/food/service/FoodService.java)
- [Food repository](../src/main/java/com/mealtalk/api/domain/food/repository/FoodRepository.java)
- [Food integration tests](../src/test/java/com/mealtalk/api/food/FoodIntegrationTests.java)
- [App food API](../../mealtalk-app/src/food/food-api.ts)
- [App food screen](../../mealtalk-app/src/food/food-screen.tsx)
