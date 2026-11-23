# Baseline (before 005) — 2026-11-23

`./mvnw verify` on the untouched code: **green**. 20 suites, 85 tests, 0 failures, 0 skipped.

## Timings (SC-006 reference)

`PerformanceSmokeIT` asserts thresholds but does not print timings, and FR-004 forbids editing it. So the
reference is the per-suite wall time from `target/failsafe-reports` / `surefire-reports`. It is noisy for
sub-second suites, so compare the larger ones and re-run when in doubt.

| Suite | Before | After |
|---|---|---|
| it.PerformanceSmokeIT (200-endpoint import + 500-edge canvas) | 1.769 s | |
| it.ConnectionsDocumentIT | 0.696 s | |
| it.MeetingToolsIT | 8.890 s | |
| it.ScenarioIT | 0.730 s | |
| it.SpecCardIT | 0.646 s | |
| canvas.CanvasServiceTest | 0.887 s | |

## API snapshots (quickstart §7)

To avoid drift from day-to-day use of the live database, the comparison does not hit `db-data` directly:
- the live DB was dumped (read-only `pg_dump`) and restored into a throwaway container;
- the baseline jar was run against that container;
- `GET /api/canvas`, `/api/connections-document`, `/api/cards` and `/api/scenarios` were saved (sorted with `jq -S`).

The dump, the baseline jar and the snapshots live in the session scratchpad, outside the repo. The final
check runs the new jar against the same restored dump and diffs the four outputs.
