# Implementation Plan: Meeting Tools — Flow Walkthroughs, Undo/Redo, Ownership, Notes

**Branch**: `004-meeting-tools` | **Date**: 2026-11-22 | **Spec**: [spec.md](spec.md)

## Summary

Four additions on top of feature 003, delivered in the user's order: (1) steps on connections and a
walkthrough mode for flows, (2) per-session undo/redo, (3) ownership details (team, repository, docs) on
microservices with a team filter, (4) sticky notes on the canvas. Backend changes are additive columns, one
new table, one new endpoint group and additive optional properties in the connections and scenario
documents; undo/redo and walkthroughs are frontend-only.

## Technical Context

Same stack as 003 (see `specs/003-declared-connections/plan.md`): Java 21 / Spring Boot 3.5 / Flyway / JPA,
H2 file DB in dev and PostgreSQL 16 in tests; React 18 + TypeScript strict + @xyflow/react 12 + TanStack Query
+ Zustand; Vitest and Playwright.

- **Storage**: Flyway `V11__meeting_tools.sql` (both vendors): `connection.step`, `microservice.team`,
  `microservice.repository_url`, `microservice.docs_url`, table `note`.
- **Performance**: walkthrough step change is a view-state change only (no request); SC-001 < 1 s.
- **Constraints**: the user's live H2 database (`backend/data`) must be backed up before V11 runs.

## Constitution Check (v2.0.0)

- **I Deterministic parsing**: untouched; notes and ownership never derive from specs. ✅
- **II Declared connections**: steps are declared by the user (document or UI), never inferred; document
  round-trip extended to steps and ownership (SC-003). ✅
- **III Layout**: notes are positioned nodes; dragged positions persist (in the note row). ✅
- **IV Scenario sandbox**: draft notes, proposed steps and proposed teams live in the scenario; undo in draft
  acts on the draft only (FR-010); baseline notes read-only in drafts. ✅
- Spec traceability: FR ids cited in code comments as in 003. ✅

## Project Structure

```text
backend/src/main/java/com/refinements/visualizer/
  connection/      Connection.step, ConnectionService (append/clear rules), FlowService.steps (reorder), FlowController PUT /flows/{id}/steps
  connection/document/  reader/writer/diff/service: connections[].step, microservices[].team/repository/docs
  microservice/    Microservice team/repositoryUrl/docsUrl, Ownership URL validation
  note/            Note entity, NoteRepository, NoteService, NoteController (/api/notes)
  canvas/          CanvasResponse.notes, MicroserviceNodeData ownership, ConnectionEdgeData.step
  scenario/model/  ProposedConnection.step, ConnectionChange.step, ProposedMicroservice.team, ProposedNote, ScenarioContent.notes
frontend/src/
  walkthrough/     steps.ts (ordering), WalkthroughBar.tsx, StepBadge in ConnectionEdge
  history/         historyStore.ts (stack, id remap), useUndoKeys, UndoButtons.tsx; recording in api/queries.ts
  microservices/   ownership fields in MicroserviceForm, TeamFilter.tsx
  notes/           NoteNode.tsx, notesApi (queries), draft notes in scenarioStore
```

## Phases

- Phase 0 — [research.md](research.md)
- Phase 1 — [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml), [quickstart.md](quickstart.md)
- Phase 2 — [tasks.md](tasks.md)
