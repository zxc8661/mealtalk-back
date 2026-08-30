# MealTalk backend documentation

This is the canonical index for the MealTalk backend. It links the stage documents in the order the features were built and shipped: profile, then private food, then meal. Each stage document is the authoritative record for its own shipped contract, application flow, migrations, and verification evidence.

Only the features documented below are delivered. There is no shared editable food catalog, no admin or role system, no password accounts, no barcode or AI capture, no external food imports, no generic unit conversion, and no standalone meal-item endpoint. Meal items exist only inside the meal aggregate.

## Stages in execution order

1. **[Profile and nutrition-target CRUD](profile-target-crud.md)**
   The authenticated profile contract and the profile screen that consumes it. Covers replace-all nutrition-target semantics including removal of every target, profile and target validation, per-user scoping, and error behavior.

2. **[Private custom-food CRUD](private-food-crud.md)**
   The private per-user food API under `/api/v1/foods` and the food-management screen. Covers owner-only visibility, name search, archive-not-delete semantics with preserved history, validation of the editable fields, and the additive ownership migration.

3. **[Meal aggregate CRUD](meal-aggregate-crud.md)**
   The meal aggregate API under `/api/v1/meals` and the daily meal journal screen. Covers aggregate-only items with full replacement, server-derived immutable item snapshots and totals, deterministic ordering, owner scoping, and the additive history-snapshot migration.

## Running it locally

**[Local run and test guide](local-testing.md)**
Starts from nothing running: Postgres container, backend on the `e2e` profile, Expo web, fixture login, and the manual scenarios to walk. No Google client ID or `.env` file is needed for that path.

## Verification

The following commands are recorded as passing in the execution ledger and backed by stage evidence:

```sh
mealtalk-back/gradlew.bat -p mealtalk-back test --rerun-tasks
git -C mealtalk-back diff --check
npm --prefix mealtalk-app run lint
cd mealtalk-app && npx tsc --noEmit -p tsconfig.json
git -C mealtalk-app diff --check
```

The TypeScript check must run from inside `mealtalk-app` so that `tsconfig.json` resolves against the app rather than the repository root. The equivalent `cd mealtalk-app && npm exec tsc -- --noEmit -p tsconfig.json` form is also recorded as passing.

Stage documents additionally record narrower test-scoped Gradle invocations for their own slices; the full-suite command above subsumes them.

Per-stage evidence, including browser QA artifacts and the recorded interception caveats, is cited in each stage document's evidence section. The [execution ledger](../../.omo/start-work/ledger.jsonl) records the independently verified result and commands for every stage.
