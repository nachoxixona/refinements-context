# Baseline (before 005) — 2026-11-23

`./mvnw verify` on the untouched code: **green**. 20 suites, 85 tests, 0 failures, 0 skipped.

## Timings (SC-006 reference)

`PerformanceSmokeIT` asserts thresholds but does not print timings, and FR-004 forbids editing it. So the
reference is the per-suite wall time from `target/failsafe-reports` / `surefire-reports`. It is noisy for
sub-second suites, so compare the larger ones and re-run when in doubt.

First reading, taken on the baseline commit before 005 (single run):

| Suite | Before |
|---|---|
| it.PerformanceSmokeIT (200-endpoint import + 500-edge canvas) | 1.769 s |
| it.ConnectionsDocumentIT | 0.696 s |
| it.MeetingToolsIT | 8.890 s |
| it.ScenarioIT | 0.730 s |
| it.SpecCardIT | 0.646 s |
| canvas.CanvasServiceTest | 0.887 s |

### After (2026-11-23, T063)

A single run is too noisy to judge 10%, so the baseline commit (`c88a5f4`, in a git worktree) and the final
code were run **alternately, three rounds each**, on the same machine and load. Mean of three:

| Suite | Baseline `c88a5f4` | After 005 | Change |
|---|---|---|---|
| it.PerformanceSmokeIT (200-endpoint import + 500-edge canvas) | 2.037 s | 2.013 s | −1% |
| it.ConnectionsDocumentIT | 0.829 s | 0.767 s | −7% |
| it.SpecCardIT | 0.674 s | 0.688 s | +2% |
| it.MeetingToolsIT + it.ScenarioIT (see note) | 9.562 s | 10.134 s | +6% |
| CanvasServiceTest (mocked APIs) | 1.061 s | 0.854 s | −20% |

Note: whichever of MeetingToolsIT and ScenarioIT runs first also pays the Spring context startup (about 8.5 s),
and the order differed between the two builds, so only their sum is comparable. Most of that sum is context
startup, which grew slightly with the extra adapter beans; it is not a request path. The operations SC-006 names
(canvas load, connection edits, document import) are covered by PerformanceSmokeIT and ConnectionsDocumentIT,
which are unchanged or faster. **SC-006 met** (no measured operation more than 10% slower).

## API snapshots (quickstart §7)

To avoid drift from day-to-day use of the live database, the comparison does not hit `db-data` directly:
- the live DB was dumped (read-only `pg_dump`) and restored into a throwaway container;
- the baseline jar was run against that container;
- `GET /api/canvas`, `/api/connections-document`, `/api/cards` and `/api/scenarios` were saved (sorted with `jq -S`).

The dump, the baseline jar and the snapshots live in the session scratchpad, outside the repo. The final
check runs the new jar against the same restored dump and diffs the four outputs.

### Final check (2026-11-23, T061/T062)

- After every module step, and at the end: the new jar against the restored dump gave the same
  `/v3/api-docs/api` (ignoring `servers`), `/api/canvas`, `/api/connections-document`, `/api/cards` and
  `/api/scenarios` as the baseline jar, byte for byte (after `jq -S`). One saved scenario's detail and export were
  also identical (export without `exportedAt`).
- Live data (read only): the new jar on `:18081` against the live `db-data` Postgres returned the same canvas,
  connections document, cards and scenarios as the running pre-005 backend. Flyway: "Successfully validated 1
  migration", "Schema public is up to date. No migration necessary" (FR-003).
- Frontend: `git diff c88a5f4 -- frontend/` is empty, and the Playwright e2e suite passed (2 tests) against the
  restructured backend (SC-002, FR-005).
