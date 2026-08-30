# Profile and nutrition-target CRUD

This document records the completed profile stage only. It covers the shipped profile contract and the profile screen that consumes it; it does not describe food or meal features.

## Authenticated API contract

All profile operations use the authenticated user identified by the bearer JWT. The client does not provide a user ID.

| Method and endpoint | Success | Authentication failure | Purpose |
| --- | --- | --- | --- |
| `GET /api/v1/me` | `200 OK` | `401 Unauthorized` | Returns the current user's profile state and targets. |
| `PUT /api/v1/me/profile` | `200 OK` | `401 Unauthorized` | Replaces the current user's profile values and complete target collection. |

A successful `GET /api/v1/me` or profile `PUT` returns:

```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "MealTalk User",
  "profileCompleted": true,
  "timezone": "Asia/Seoul",
  "profile": {
    "heightCm": 170,
    "weightKg": 65,
    "activityLevel": "MEDIUM",
    "goalMode": "MAINTAIN"
  },
  "targets": [
    {
      "targetType": "DAILY_CALORIES",
      "targetValue": 2000,
      "dueDate": null
    }
  ]
}
```

`profile` is `null` before a profile is saved. `targets` is a list and can be empty. The supported enum values are:

- `activityLevel`: `LOW`, `MEDIUM`, `HIGH`
- `goalMode`: `LOSS`, `MAINTAIN`, `GAIN`
- `targetType`: `TARGET_WEIGHT`, `DAILY_CALORIES`, `DAILY_PROTEIN`

### Replace profile and targets

`PUT /api/v1/me/profile` accepts the complete desired state:

```json
{
  "heightCm": 170,
  "weightKg": 65,
  "activityLevel": "MEDIUM",
  "goalMode": "MAINTAIN",
  "targets": [
    {
      "targetType": "TARGET_WEIGHT",
      "targetValue": 60,
      "dueDate": "2026-12-31"
    },
    {
      "targetType": "DAILY_PROTEIN",
      "targetValue": 120,
      "dueDate": null
    }
  ]
}
```

Targets have replace-all semantics, not patch semantics:

- A submitted target type is created if absent or updated if it already belongs to the authenticated user.
- A previously stored target omitted from `targets` is deleted for that authenticated user.
- `"targets": []` is valid and deletes all of that user's existing targets.
- A successful save sets `profileCompleted` to `true`, including a save with no targets.
- There is no separate target mutation endpoint in this stage.

The replacement executes in the authenticated user's transaction. Loading, updating, and deleting targets are all scoped to that user's ID; another user's targets are not loaded or deleted.

## Validation and errors

Malformed or invalid profile update input returns `400 Bad Request`.

- `heightCm`, `weightKg`, `activityLevel`, `goalMode`, and `targets` are required.
- `heightCm` and `weightKg` must be positive numbers.
- Every target requires a valid `targetType` and a positive `targetValue`.
- `dueDate` is optional; when supplied it must be a strictly future calendar date.
- Each `targetType` may occur at most once in a request. Duplicate types return `400 Bad Request`.

Validation failure and duplicate-type rejection leave the prior profile and targets unchanged. Requests without a usable JWT receive `401 Unauthorized` before either profile endpoint is handled.

## Application flow and states

The authenticated app uses `GET /api/v1/me` to choose the profile surface:

1. While the request is pending, it renders a profile loading state.
2. A load failure renders an error state with retry.
3. `profileCompleted: false` opens first-time setup; `true` opens profile maintenance.
4. The form restores the returned profile and enabled targets. With no targets, it shows an explicit empty-target state.
5. Saving sends the complete enabled target set to `PUT /api/v1/me/profile`; disabling a target omits it, and disabling every target sends `[]`.
6. Client validation shows adjacent errors for non-positive values and invalid/past dates. A save is pending/disabled and duplicate activations result in one `PUT`.
7. A `400` response keeps the edited draft and presents an error. A `401` clears the persisted session and returns to login. A successful save repopulates the form from the response and shows success feedback; later edits clear that stale success feedback.

## E2E fixture and live-database caveat

The explicit Spring `e2e` profile replaces Google verification with the fixed fixture token `mealtalk-e2e-id-token` for `POST /api/v1/auth/google`; the default profile rejects that fixture. This supports deterministic browser authentication without an interactive Google credential.

The browser profile QA used deterministic intercepted HTTP fixtures that match the read/update DTO contract, not a live backend database. A live `e2e` backend launch was attempted but could not connect to PostgreSQL at `localhost:5433` because the workstation service was unavailable and Docker Desktop was not running. Backend profile behavior was verified separately through authenticated MockMvc and H2 integration tests.

## Verified evidence and commands

- Backend target replacement, empty-list deletion, duplicate and invalid-input atomicity, and cross-user isolation: [task 4 evidence](../../.omo/evidence/task-4-staged-manual-crud.txt).
- App setup/edit, replace-all UI behavior, validation, pending, retry, `400`, and `401` browser QA: [task 6 browser assertions](../../.omo/evidence/task-6-staged-manual-crud.browser.txt) and [task 6 evidence](../../.omo/evidence/task-6-staged-manual-crud.txt).
- The recorded live-backend limitation is in [task 6 backend log](../../.omo/evidence/task-6-backend.log).
- The execution ledger records the independent verification results and commands: [ledger](../../.omo/start-work/ledger.jsonl).

The following commands are recorded as passing:

```sh
mealtalk-back/gradlew.bat -p mealtalk-back test --rerun-tasks
mealtalk-back/gradlew.bat -p mealtalk-back test
npm --prefix mealtalk-app run lint
cd mealtalk-app && npm exec tsc -- --noEmit -p tsconfig.json
git -C mealtalk-app diff --check
git -C mealtalk-back diff --check
```

The TypeScript command intentionally changes into `mealtalk-app`: the otherwise similar `npm --prefix mealtalk-app exec tsc -- --noEmit -p tsconfig.json` was recorded as failing on this Windows/npm setup because `tsc` searched the repository root for `tsconfig.json`. See [the captured invocation](../../.omo/evidence/task-6-tsc-requested-invocation.txt).

## Implementation references

- [Profile controller](../src/main/java/com/mealtalk/api/domain/auth/controller/AuthController.java)
- [Profile service](../src/main/java/com/mealtalk/api/domain/auth/service/ProfileService.java)
- [Profile update request](../src/main/java/com/mealtalk/api/domain/auth/dto/ProfileUpdateRequest.java)
- [Current-user response](../src/main/java/com/mealtalk/api/domain/auth/dto/CurrentUserResponse.java)
- [Backend integration tests](../src/test/java/com/mealtalk/api/auth/AuthIntegrationTests.java)
- [App profile API](../../mealtalk-app/src/profile/profile-api.ts)
- [App profile screen](../../mealtalk-app/src/profile/profile-screen.tsx)
