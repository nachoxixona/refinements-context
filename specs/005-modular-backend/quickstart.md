# Quickstart: Validating the Modular Backend (005)

This guide checks that the restructure works and that behaviour did not change. Run every command from the
repository root unless it says otherwise.

## Prerequisites

- Java 21 and Docker (Testcontainers starts a throwaway Postgres for the integration tests).
- The dev database is up: `docker compose up -d db`. **Never** run `docker compose down -v`, because the `db-data`
  volume holds the live data.

## 1. Baseline (once, before any change)

```bash
git init && git add -A && git commit -m "Baseline before 005 modular backend"
git status --ignored | grep -E "backend/data|node_modules|target"   # must be listed as ignored
cd backend && ./mvnw verify                                           # all green = the baseline
```

Record the `PerformanceSmokeIT` timings from this run. They are the SC-006 reference.

## 2. After each migration step (one module per commit)

```bash
cd backend && ./mvnw verify
```

What to expect:
- every existing unit test and IT passes with unchanged assertions (SC-001);
- `ContractConformanceIT` passes against `specs/004-meeting-tools/contracts/openapi.yaml`
  (the REST API is unchanged);
- once the cycles are broken, `ModularityTest` passes, reporting 0 cycles and 0 boundary violations
  (SC-003). Before that, the expected violations it reports are recorded in `tasks.md`.

## 3. Prove the boundary check bites (SC-003)

Temporarily inject another module's internal repository into `canvas`, for example:
`card.adapter.out.persistence.SpecCardJpaRepository` in `CanvasService`.

```bash
cd backend && ./mvnw test -Dtest=ModularityTest   # must FAIL naming canvas → card internal
```

Then revert the change.

## 4. Domain tests without infrastructure (SC-005)

```bash
cd backend && ./mvnw test -Dtest='*DomainTest'
```

Each of card, microservice, connection and scenario has at least one domain test that runs with in-memory
port fakes. No Spring context and no container start.

## 5. Module documentation (US3)

```bash
cd backend && ./mvnw test -Dtest=ModularityTest
ls ../docs/modules/   # components.puml (all modules) + one module-<name>.puml/.adoc per module
```

The diagram's arrows must match the allowed-dependency list in
[contracts/module-apis.md](./contracts/module-apis.md).

## 6. End to end (SC-002)

```bash
cd frontend && npx playwright test     # uses the throwaway viz-e2e-db container on port 55433
```

All tests must pass with **no frontend changes**: `git diff --stat frontend/` must be empty.

## 7. Live data smoke check

Start the app against the real `db-data` database (no migration runs, so Flyway reports V1 as up to date).
Then compare against the baseline:
- `GET /api/canvas`
- `GET /api/connections-document`

```bash
docker compose up -d --build
curl -s localhost:8080/api/canvas | jq -S . > after-canvas.json                  # outside the repo; compare to
curl -s localhost:8080/api/connections-document | jq -S . > after-doc.json      # the same calls on the baseline build
```

The two outputs must be identical, except for any fields that are already time-dependent.
