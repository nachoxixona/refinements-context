# Tasks: Meeting Tools — Flow Walkthroughs, Undo/Redo, Ownership, Notes

**Input**: spec.md, plan.md, research.md, data-model.md, contracts/openapi.yaml
**Tests**: included (regression and contract tests are a constitution quality gate).

## Phase 1: Setup

- [X] T001 Back up the user's database `backend/data` to `backend/data.backup-004-before-meeting-tools` before the backend restarts with V11
- [X] T002 Flyway `V11__meeting_tools.sql` in `backend/src/main/resources/db/migration/{h2,postgresql}/`: `connection.step`, `microservice.team|repository_url|docs_url`, table `note` (data-model.md §1–§3)

## Phase 2: User Story 1 — Walk through a flow (P1)

- [X] T003 [US1] `Connection.step` (data-model.md §1 Connection.step) in `backend/.../connection/Connection.java`; append/clear rules in `ConnectionService` create/update (`ConnectionCreate.step`, `ConnectionPatch.step`); `FlowService.delete` clears steps
- [X] T004 [US1] `PUT /api/flows/{flowId}/steps` (`FlowService.setSteps`, `FlowController`) — must list exactly the flow's connections
- [X] T005 [US1] `ConnectionEdgeData.step`, `ConnectionDto.step` in canvas/connection DTOs
- [X] T006 [US1] Document `connections[].step` (data-model.md §5): reader (`STEP_WITHOUT_FLOW`, `INVALID_STEP`), writer, diff (`stepBefore/After`), apply (append when absent)
- [X] T007 [US1] Scenario `ProposedConnection.step`, `ConnectionChange.step` (data-model.md §4) in model + validator
- [X] T008 [P] [US1] Backend tests: step rules (ConnectionIT), reorder endpoint (FlowIT), document step round-trip and errors (validator/diff/IT)
- [X] T009 [US1] Frontend `walkthrough/steps.ts` (ordering, R2) + tests; step badge on `ConnectionEdge`; step input in `ConnectionDetailsPanel` and `NewConnectionPrompt`; overlay applies proposed steps
- [X] T010 [US1] Walkthrough: `canvasStore.walkthrough`, highlight (`walkthrough` mode), `WalkthroughBar.tsx` (Prev/Next/End, ←/→/Esc, fit view), ▶ in Legend; reorder (▲/▼) in `FlowsPanel`
- [X] T011 [US1] e2e: steps and walkthrough in `frontend/tests/e2e/meeting-tools.spec.ts`

## Phase 3: User Story 2 — Undo/redo (P2)

- [X] T012 [US2] `frontend/src/history/historyStore.ts`: stack (50), redo clearing, id remap, running guard, failure reporting; unit tests
- [X] T013 [US2] Record entries in `api/queries.ts` mutation hooks (microservices, cards link/unlink, connections, flows, steps, document apply, notes) using cache snapshots; restore of deleted microservices (name, description, ownership, position, card, collapse; connections re-attach by name)
- [X] T014 [US2] Layout entries (drag, auto-arrange, collapse) via `canvasStore.actions.applyLayout` in `ArchitectureCanvas.tsx`
- [X] T015 [US2] Draft mode: snapshot history of `scenarioStore.draft`; new history on mode switch (FR-010)
- [X] T016 [US2] `UndoButtons.tsx` in toolbar + Ctrl/⌘+Z, Ctrl+Shift+Z, Ctrl+Y (ignored while typing); "can't be undone" in import/re-sync/delete-card confirmations (FR-011)
- [X] T017 [US2] e2e: delete microservice → undo → redo; move → undo

## Phase 4: User Story 3 — Ownership (P3)

- [X] T018 [US3] `Microservice.team|repositoryUrl|docsUrl` (data-model.md §2) with validation in `MicroserviceService`; Write/Patch/Dto; `MicroserviceNodeData`
- [X] T019 [US3] Document `microservices[].team|repository|docs` (data-model.md §5): reader (`INVALID_URL`), writer, diff, apply
- [X] T020 [US3] `ProposedMicroservice.team` (data-model.md §4) and in the proposed microservice editor/form
- [X] T021 [P] [US3] Backend tests: ownership validation (MicroserviceIT), document round-trip
- [X] T022 [US3] Frontend: ownership fields in `MicroserviceForm`, team + links on `MicroserviceNode`, `TeamFilter.tsx` (highlight mode `team`), name search matches team
- [X] T023 [US3] e2e: ownership and team filter

## Phase 5: User Story 4 — Notes (P4)

- [X] T024 [US4] `note/` package: entity (data-model.md §3 Note.text, Note.kind, Note.position), repository, service, controller `/api/notes`; `CanvasResponse.notes`
- [X] T025 [US4] Scenario `ScenarioContent.notes` / `ProposedNote` (data-model.md §4) + validator
- [X] T026 [P] [US4] Backend tests: NoteIT, scenario notes round-trip
- [X] T027 [US4] Frontend: `NoteNode.tsx`, `+ Note` toolbar button, edit/kind/delete, drag saves position, draft notes in `scenarioStore`, baseline notes read-only in drafts
- [X] T028 [US4] e2e: notes persist; draft notes stay in the draft

## Phase 6: Polish

- [X] T029 Contracts `contracts/openapi.yaml` + `connections-document.schema.json` updated; served schema copy; `docs/connections-document.md` and README
- [X] T030 Full gates (`./mvnw verify`, typecheck, lint, Vitest, Playwright); restart backend on the user's data after T001; verify

## Dependencies & Execution Order

Phase 1 → US1 → US2 (records US1 step edits) → US3 → US4 (US2 records note edits once US4 lands) → Polish.
