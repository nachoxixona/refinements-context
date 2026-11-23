---

description: "Task list for feature 003: Declared Connections & Spec Cards"
---

# Tasks: Declared Connections & Spec Cards

**Input**: Design documents from `/specs/003-declared-connections/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml,
contracts/connections-document.schema.json, quickstart.md

**Tests**: Included. Constitution v2.0.0 (Principle V and the Quality Gates) requires JUnit
coverage of parsing and of the connections document (validation, import, export, round trip), and
edge-model tests proving survival. The spec's Independent Tests map to the integration tests
below.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested
on its own.

**Data safety**: `backend/data/` is the user's live 002 database. Never delete or reset it.
Until US6 (migration) is done, run the dev backend only with the `scratch` profile (T003).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on unfinished tasks)
- **[Story]**: the user story the task belongs to (US1–US6)

## Path Conventions

- Backend: `backend/src/main/java/com/refinements/visualizer/` (written `be/` below),
  `backend/src/main/resources/`, and `backend/src/test/java/com/refinements/visualizer/`
  (written `bt/`)
- Frontend: `frontend/src/` (written `fe/`) and `frontend/tests/` (written `ft/`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Contract, dependencies and a safe dev database before any code changes.

- [X] T001 Replace the frontend type source: point `gen:api` in `frontend/package.json` at `specs/003-declared-connections/contracts/openapi.yaml`, run it to regenerate `fe/api/schema.d.ts`, and point `ContractConformanceIT` in `bt/it/ContractConformanceIT.java` at the same contract file
- [X] T002 [P] Add `@uiw/react-codemirror` ^4.23, `@codemirror/lang-json` ^6 and `@codemirror/lint` ^6.8 to `frontend/package.json` (research R12, R15) and run `npm install`
- [X] T003 [P] Add an H2 `scratch` profile in `backend/src/main/resources/application-scratch.yml`, with its datasource URL at `jdbc:h2:file:./data-scratch/visualizer;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`. Add `backend/data-scratch/` to `.gitignore`, and document it in `README.md` as the profile to use while developing 003
- [X] T004 [P] Create connections-document fixtures in `backend/src/test/resources/connections/`:
  - `shop.json`: 5 microservices and 7 connections, including two Orders → Billing connections and a Billing → Orders event;
  - `invalid-mixed.json`: one error of each kind in data-model.md §4;
  - `invalid-syntax.json`;
  - `large-200.json`: 200 connections between 100 microservices;
  - `roundtrip/*.json`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Remove inference, create the new schema and entities, and serve an (initially
empty) canvas. Every story depends on this phase.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T005 Write Flyway `V4__cards_microservices_connections.sql` in both `backend/src/main/resources/db/migration/postgresql/` and `…/h2/`. It creates:
  - `spec_card` (data-model.md §1);
  - `microservice` (§2), with `UNIQUE(name_normalized)` and `UNIQUE(spec_card_id)`, plus FK `ON DELETE SET NULL`;
  - `connection` (§3), with `UNIQUE(source_name_normalized, target_name_normalized, name_normalized)`, `CHECK(source_name_normalized <> target_name_normalized)`, FKs `ON DELETE SET NULL`, and a `CHECK` on `kind IN ('REQUEST','EVENT')`;
  - `workspace_state` (§6), seeded with one row at revision 0;
  - `migration_report` (§8);
  - a new column `baseline_layout.collapsed_cards` (§5), defaulting to `[]`.

  Old tables stay untouched.
- [X] T006 Delete the inference and edge-override backend:
  - `be/inference/` (move `FieldNormalizer.java` to `be/ingestion/` first);
  - `be/edge/`;
  - `be/graph/`;
  - the service- and edge-key parts of `be/ingestion/ServiceKeys.java` (`serviceKey`, `nextFreeKey`, `edgeKey`, `edgeKeyHash`). Keep `endpointKey`, `normalizePath` and `sha256Hex`, still used by `FieldFlattener` and for `SpecCard.contentHash`, by moving them into `be/ingestion/SpecKeys.java`;
  - the `visualizer.inference.*` block in `application.yml` and its fields in `be/common/VisualizerProperties.java`;
  - the tests `bt/inference/EdgeInferenceEngineTest.java`, `ReferencePrecisionTest.java`, `bt/graph/GraphServiceTest.java`, `bt/it/EdgeOverrideIT.java`, `InferenceSettingsIT.java`, and the service- and edge-key cases of `bt/ingestion/ServiceKeysTest.java` (renamed to `bt/ingestion/SpecKeysTest.java`, keeping the endpoint-key and hash cases), and `reference-workspace/expected-edges.json`.

  Move `bt/inference/FieldNormalizerTest.java` to `bt/ingestion/` (research R1). JPA must stop mapping `service_spec`, `edge_override` and `inference_settings`.
- [X] T007 [P] Create `Names.norm(String)` (`lower(trim(collapse whitespace))`) and its unit test in `be/connection/Names.java` and `bt/connection/NamesTest.java`. It is the normalization rule for `Microservice.nameNormalized` and `Connection.nameNormalized` (data-model.md §2, §3)
- [X] T008 [P] Create the `SpecCard` JPA entity and `SpecCardRepository` in `be/card/`. They map data-model.md §1 `SpecCard.title`, `SpecCard.apiVersion`, `SpecCard.specFormat`, `SpecCard.sourceType`, `SpecCard.sourceUrl`, `SpecCard.rawContent`, `SpecCard.contentHash`, `SpecCard.endpoints`, `SpecCard.lastSyncAt`, `SpecCard.lastSyncStatus` and `SpecCard.lastSyncErrors`. Reuse the converters of the removed `ServiceSpec`, and move `SourceType` and `SyncStatus` to `be/card/`
- [X] T009 [P] Create the `Microservice` JPA entity and `MicroserviceRepository` (`findByNameNormalized`, `findBySpecCardId`) in `be/microservice/`. They map data-model.md §2 `Microservice.name`, `Microservice.nameNormalized`, `Microservice.description` and `Microservice.specCardId`
- [X] T010 [P] Create the `Connection` JPA entity, the `ConnectionKind` enum (`REQUEST`, `EVENT`) and `ConnectionRepository` in `be/connection/`. The repository needs `findBySourceIdOrTargetId`, the dangling query `sourceId IS NULL OR targetId IS NULL`, and `findByKey(srcNorm, tgtNorm, nameNorm)`. They map data-model.md §3 `Connection.name`, `Connection.nameNormalized`, `Connection.kind`, `Connection.description`, `Connection.sourceId`, `Connection.targetId`, `Connection.sourceName`, `Connection.targetName`, `Connection.sourceNameNormalized` and `Connection.targetNameNormalized`
- [X] T011 [P] Create the `WorkspaceState` entity and the `WorkspaceRevision` service in `be/workspace/`. `bump()` returns the new revision; `check(baseRevision)` throws a stale-revision error mapped to `409`. Both use data-model.md §6 `WorkspaceState.revision` (research R7)
- [X] T012 [P] Extend `be/layout/BaselineLayout.java`, `LayoutUpdate.java` and `LayoutController.java`. Use data-model.md §5 `BaselineLayout.positions` with `ms:`/`card:` keys, and add `BaselineLayout.collapsedCards`. Key pruning on write uses the microservice and card repositories. `BaselineLayout.version` stays the optimistic lock
- [X] T013 Extend `be/common/ApiExceptionHandler.java` and `be/common/errors/`:
  - `ConflictProblem`, with `conflict` set to `DUPLICATE_NAME`, `DUPLICATE_CONNECTION`, `SELF_CONNECTION`, `MICROSERVICE_HAS_CARD`, `CARD_ALREADY_LINKED` or `DUPLICATE_SCENARIO_NAME`;
  - `DuplicateCardProblem`;
  - `ConnectionsDocumentProblem`, a 422 carrying an `errors[]` list of `DocumentError`.

  Use the shapes in contracts/openapi.yaml.
- [X] T014 Create `CanvasService` and `CanvasController` (`GET /api/canvas`), and the DTO records `CanvasNode`, `MicroserviceNodeData`, `SpecCardNodeData`, `CanvasEdge`, `ConnectionEdgeData`, `Connection` and `CanvasResponse`, in `be/canvas/` and `be/canvas/dto/`, with `@Schema` names matching the contract. CanvasService builds:
  - microservice nodes `ms:{id}`;
  - unlinked card nodes `card:{id}` with their saved position;
  - docked card nodes with `parentId=ms:{id}`, `draggable=false` and a relative position (research R8), omitted when the card is in `collapsedCards`;
  - edges `connection:{id}` for attached connections;
  - `danglingConnections`, plus `revision` and `layoutVersion`.
- [X] T015 [P] Unit test `bt/canvas/CanvasServiceTest.java` covering docked vs. unlinked cards, collapsed cards omitted, dangling connections listed but not drawn, and several connections per pair as separate edges
- [X] T016 Update `bt/it/LayoutAndGraphIT.java`, renamed to `bt/it/LayoutAndCanvasIT.java`, to cover the empty canvas, saving positions and `collapsedCards`, a stale layout version returning `409`, and pruning of unknown keys
- [X] T017 Frontend cleanup in one change:
  - delete `fe/canvas/edges/InferredEdge.tsx`, `VerifiedEdge.tsx` and `retargetEdges.ts`, `fe/canvas/EdgeFilterBar.tsx`, `fe/edges/`, `fe/settings/`, `ft/canvas/retargetEdges.test.ts`, and the inference queries in `fe/api/queries.ts`;
  - replace `useGraph` with `useCanvas` (`GET /api/canvas`) in `fe/api/queries.ts` and `fe/api/types.ts`;
  - update `fe/canvas/viewModel.ts`, `fe/canvas/useCanvasView.ts` and `fe/canvas/ArchitectureCanvas.tsx` to render `CanvasResponse`. Use temporary placeholder node components until US1 and US2 land.

  `npm run typecheck` and `npm run lint` must pass.
- [X] T018 Update `fe/canvas/layout/elkLayout.ts` and `ft/canvas/elkLayout.test.ts`. A microservice and its docked card form one ELK block with their combined measured size, unlinked cards go in a separate final row, and positioned nodes are never moved without **Auto-arrange** (research R8, 002/R11)

**Checkpoint**: The backend builds with the new schema, `GET /api/canvas` returns an empty
canvas, and the frontend renders it. Inference is gone.

---

## Phase 3: User Story 1 - Import specs as spec cards (Priority: P1) 🎯 MVP

**Goal**: Import OpenAPI/Swagger by paste or URL as unlinked cards that show title, endpoints,
method badges and expandable fields. Re-sync and delete cards. Importing never creates connections.

**Independent Test**: Import a Swagger 2.0 YAML by paste and an OpenAPI 3.x JSON by URL. Two
unlinked cards appear and no connections. Re-sync updates the card in place. Importing duplicate
content is refused.

### Tests for User Story 1

- [X] T019 [P] [US1] Rework `bt/it/ServiceRegistrationIT.java` into `bt/it/SpecCardIT.java`. It covers:
  - paste and URL import returning 201;
  - an invalid spec returning 422 with error locations and creating no row;
  - duplicates by `SpecCard.contentHash` and by `SpecCard.sourceUrl` returning `409 DUPLICATE_CARD` with `existingCardId` (data-model.md §1);
  - re-sync keeping the `id`, link and position;
  - a failed re-sync keeping the last good content;
  - delete making the linked microservice spec-less.

  It also asserts the `connection` row count is unchanged after import, sync and delete (FR-007, SC-007).
- [X] T020 [P] [US1] Update `bt/ingestion/FieldFlattenerTest.java` and `bt/ingestion/SpecParserTest.java` so endpoint keys use the `{cardId}:{METHOD} {path}` prefix (research R1). The parsing assertions are unchanged
- [X] T021 [P] [US1] Move `ft/canvas/CustomServiceNode.test.tsx` to `ft/canvas/SpecCardNode.test.tsx`. Keep the zoom tiers, method badges, field expansion and nested add-field behaviour, and add the source and last-import line (FR-009)

### Implementation for User Story 1

- [X] T022 [US1] Implement `SpecCardService` (import, sync, delete) in `be/card/SpecCardService.java`, built from the removed `ServiceRegistrationService`. It:
  - parses with `SpecParser`;
  - flattens with `FieldFlattener` using the card ID as the key;
  - detects duplicates on `SpecCard.contentHash` and `SpecCard.sourceUrl` (data-model.md §1);
  - enforces the `SpecCard.rawContent` 10 MB limit;
  - on re-sync, records `SpecCard.lastSyncAt`, `SpecCard.lastSyncStatus` and `SpecCard.lastSyncErrors` (data-model.md §1), keeping the last good `rawContent` and `endpoints` on failure;
  - nulls `Microservice.specCardId` on delete.

  It must not depend on the `connection` package.
- [X] T023 [US1] Implement `SpecCardController` and the DTOs `ImportCardRequest` and `SpecCardSummary` in `be/card/SpecCardController.java` and `be/card/dto/`. The endpoints are `GET/POST /api/cards`, `DELETE /api/cards/{cardId}` and `POST /api/cards/{cardId}/sync`, per contracts/openapi.yaml. Delete `be/service/`
- [X] T024 [US1] Add `useCards`, `useImportCard`, `useSyncCard` and `useDeleteCard` to `fe/api/queries.ts`, each invalidating the `canvas` query
- [X] T025 [P] [US1] Create `fe/cards/ImportSpecDialog.tsx` (paste or URL; shows the 422 error list with locations; on a 409 duplicate, shows "already on the canvas" with a "Show card" action) and move `fe/ingestion/ResyncButton.tsx` to `fe/cards/ResyncButton.tsx`. Delete `fe/ingestion/`
- [X] T026 [US1] Create `fe/canvas/nodes/SpecCardNode.tsx` by moving the endpoint, field, `FieldList`, `AddFieldForm` and zoom-tier rendering out of `fe/canvas/nodes/CustomServiceNode.tsx`. Show the title, version, source (pasted or URL), last import time, a re-sync button for URL cards, and delete. Use a card style visibly distinct from microservices in `fe/styles/app.css` (FR-015). Register it in `fe/canvas/nodes/nodeTypes.ts`
- [X] T027 [US1] Wire **Import spec** into the toolbar in `fe/App.tsx`. New unlinked cards get placed by the existing placement logic in `fe/canvas/ArchitectureCanvas.tsx`, and dragged card positions are saved as `card:{id}`

**Checkpoint**: US1 works on its own. Cards can be imported, viewed, re-synced and deleted,
and there are no connections.

---

## Phase 4: User Story 2 - Create microservices and link spec cards (Priority: P1)

**Goal**: Named microservice nodes, with or without a spec. Explicit 1:1 linking, with the
card docked under its microservice, collapsible, and kept on reload. Unlink, rename and delete.

**Independent Test**: Create "Orders" and "Billing" and link a card to "Orders". The card moves
with it. Linking a second card is refused unless you choose to replace. Unlinking leaves both
on the canvas.

### Tests for User Story 2

- [X] T028 [P] [US2] Integration test `bt/it/MicroserviceLinkIT.java`. It covers:
  - create, and a duplicate name (normalized; data-model.md §2 `Microservice.nameNormalized`) returning `409 DUPLICATE_NAME`;
  - rename, with a clash returning `409`;
  - describe;
  - link;
  - `409 MICROSERVICE_HAS_CARD`, then `replace=true` unlinking the old card;
  - `409 CARD_ALREADY_LINKED`;
  - unlink;
  - delete making the card unlinked;
  - connection rows (IDs, names, kinds, ends) identical before and after **unlink** and after **link with `replace=true`** (constitution Quality Gates: edges survive unlinking; FR-026).
- [X] T029 [P] [US2] Frontend test `ft/canvas/MicroserviceNode.test.tsx` covering the name, the description, the "API: {title}" badge when collapsed, and the expand and collapse toggle

### Implementation for User Story 2

- [X] T030 [US2] Implement `MicroserviceService` in `be/microservice/MicroserviceService.java`. It provides:
  - **create**: unique by data-model.md §2 `Microservice.nameNormalized`;
  - **rename**: cascades to `Connection.sourceName` and `Connection.targetName` and their normalized copies (data-model.md §3) in one transaction, for FR-028;
  - **describe** (`Microservice.description`);
  - **delete**: returns the IDs of connections left dangling;
  - **link**, with `replace`, and **unlink**, both on `Microservice.specCardId`.

  Every write calls `WorkspaceRevision.bump()`.
- [X] T031 [US2] Implement `MicroserviceController` and the DTOs `MicroserviceWrite`, `MicroservicePatch`, `Microservice` and `MicroserviceMutation` in `be/microservice/MicroserviceController.java` and `be/microservice/dto/`. The endpoints are `POST /api/microservices`, `PATCH/DELETE /api/microservices/{id}` and `PUT/DELETE /api/microservices/{id}/card`, per the contract
- [X] T032 [US2] Add `useCreateMicroservice`, `useUpdateMicroservice`, `useDeleteMicroservice`, `useLinkCard` and `useUnlinkCard` to `fe/api/queries.ts`
- [X] T033 [P] [US2] Create `fe/canvas/nodes/MicroserviceNode.tsx`. It shows the name, the description (on hover or in detail), the "API: {title}" badge when the card is collapsed, a collapse toggle when a card is docked, and source and target handles for connections. Give it a microservice style distinct from cards in `fe/styles/app.css` (FR-015), and register it in `fe/canvas/nodes/nodeTypes.ts`. Then delete `fe/canvas/nodes/CustomServiceNode.tsx` (emptied by T026) and the temporary placeholder nodes from T017
- [X] T034 [P] [US2] Create `fe/microservices/MicroserviceForm.tsx` (create, rename and describe, showing the 409 duplicate-name message) and `fe/microservices/LinkConflictDialog.tsx` ("Replace the current spec?" on `MICROSERVICE_HAS_CARD`, an explanation on `CARD_ALREADY_LINKED`)
- [X] T035 [US2] In `fe/canvas/ArchitectureCanvas.tsx`:
  - dropping an unlinked card on a microservice links it (`onNodeDragStop` hit test);
  - add a card menu action **Link to…**;
  - unlinking places the card at the parent position + 40 px and saves the layout (research R8);
  - collapse and expand update `collapsedCards` through `PUT /api/layout`;
  - deleting a microservice asks for confirmation, stating how many connections reference it (FR-016).
- [X] T036 [US2] Wire **New microservice** into the toolbar in `fe/App.tsx`. Microservice positions are saved as `ms:{id}`, and docked cards move with their parent

**Checkpoint**: US1 and US2 work together. Microservices exist, link 1:1, dock and collapse.

---

## Phase 5: User Story 3 - Declare connections with a JSON document and on the canvas (Priority: P1)

**Goal**: Named connections, optionally `request`/`event`, with several per pair. They can be drawn,
edited and deleted on the canvas and managed through the connections document (upload, in-app
editor, preview, replace-apply, export with a round trip). Dangling connections are flagged.

**Independent Test**: Upload `shop.json` and see 7 separate labelled arrows styled by kind.
Draw one more and the editor lists it. Export and re-import: "No changes". A malformed entry
is rejected with its position. Deleting a microservice makes its connections dangling.

### Tests for User Story 3

- [X] T037 [P] [US3] Unit test `bt/connection/document/JsonLocatorTest.java`. Pointers map to line and column for nested objects, arrays, strings containing escapes, and CRLF line endings (research R6)
- [X] T038 [P] [US3] Unit test `bt/connection/document/ConnectionsDocumentValidatorTest.java`. It covers every error kind in data-model.md §4, with the exact `path`, `pointer`, `line` and `column`; all errors reported together in document order; `DUPLICATE_CONNECTION` naming the first occurrence; and the limits from research R4. It also checks the lengths and enum values of `Connection.name`, `Connection.kind` and `Connection.description` and of `Microservice.name` and `Microservice.description` (data-model.md §2–§3)
- [X] T039 [P] [US3] Unit test `bt/connection/document/ConnectionsDocumentDiffTest.java`. It covers connections added, changed (kind or description, keeping the ID) and removed; dangling connections matched by name; microservices added from a list entry and from a connection end; and a description that is absent (no change), null (clears it) or different (changed). Microservices are never removed, and an identical document gives `unchanged=true`
- [X] T040 [P] [US3] Unit test `bt/connection/document/ConnectionsDocumentWriterTest.java`. The output is deterministic (sorted microservices and connections, absent optional properties omitted, two-space indent, trailing newline); every microservice is listed; dangling connections are excluded and counted; and writer → reader → diff gives `unchanged` for each `connections/roundtrip/*.json` fixture
- [X] T041 [P] [US3] Integration test `bt/it/ConnectionIT.java`. It covers:
  - create, with a duplicate per pair returning `409 DUPLICATE_CONNECTION`, a self connection returning `409 SELF_CONNECTION`, and the same name on another pair being allowed;
  - opposite directions;
  - patch of name and kind, and delete;
  - deleting a microservice leaving its connections dangling (`Connection.sourceId` null and `Connection.sourceName` kept; data-model.md §3);
  - recreating by name re-attaching them;
  - reassign;
  - every write bumping the revision.
- [X] T042 [P] [US3] Integration test `bt/it/ConnectionsDocumentIT.java`. It covers:
  - preview changing nothing;
  - apply replacing the baseline and creating missing microservices without a spec;
  - an invalid document returning 422 with nothing changed;
  - a stale `baseRevision` returning 409;
  - an over-2 MB document returning 413;
  - export → apply giving `unchanged` (SC-003);
  - applying the same document twice duplicating nothing;
  - `large-200.json` previewed and applied in under 5 s (SC-005).
- [X] T043 [P] [US3] Frontend unit tests in `ft/canvas/connectionEdges.test.ts`:
  - `parallelOffsets` gives n connections per unordered pair symmetric offsets, so opposite directions never overlap;
  - `connectionStyle` returns solid, dashed or dotted by kind, plus the proposed overlay;
  - `bundle` merges a flow into `×n` below the overview tier (research R11).
- [X] T044 [P] [US3] Frontend test `ft/connections/ConnectionsDocumentEditor.test.tsx` with a mocked API. Save calls preview, shows the change dialog and then applies with `baseRevision`; a 422 shows line markers; closing with unsaved changes asks to save or discard; Upload opens the same preview

### Implementation for User Story 3

- [X] T045 [US3] Implement `ConnectionService` in `be/connection/ConnectionService.java`. It provides create, patch, delete and reassign, applying the rules of data-model.md §3 to `Connection.name`, `Connection.kind`, `Connection.description`, `Connection.sourceId`/`Connection.targetId` and the normalized name key, and it bumps the revision. Add the dangling re-attach on microservice create to `MicroserviceService.create` (research R2), returning `reattachedConnectionIds`
- [X] T046 [US3] Implement `ConnectionController` and the DTOs `ConnectionCreate`, `ConnectionPatch` and `ConnectionMutation` in `be/connection/ConnectionController.java` and `be/connection/dto/`. The endpoints are `POST /api/connections`, `PATCH/DELETE /api/connections/{id}` and `POST /api/connections/{id}/reassign`
- [X] T047 [P] [US3] Implement `JsonLocator` in `be/connection/document/JsonLocator.java`. One Jackson streaming pass records JSON Pointer → start line and column (research R6)
- [X] T048 [US3] Implement `ConnectionsDocumentReader` and `ConnectionsDocumentValidator` in `be/connection/document/`:
  - the text is parsed with a syntax error giving line and column;
  - the tree walk collects `WRONG_TYPE`, `MISSING`, `TOO_LONG`, `INVALID_KIND`, `SELF_CONNECTION`, `DUPLICATE_CONNECTION`, `DUPLICATE_MICROSERVICE`, `UNKNOWN_PROPERTY`, `TOO_MANY` and `UNSUPPORTED_VERSION`, each positioned through `JsonLocator`;
  - kinds map lowercase `request`/`event` to `ConnectionKind`;
  - it produces an immutable `ConnectionsDocumentModel`.

  Rules come from research R4 and data-model.md §4.
- [X] T049 [US3] Implement `ConnectionsDocumentDiff` (the R5 rules, keyed by the `Names.norm` triple) and `ConnectionsDocumentWriter` (deterministic output, research R4) in `be/connection/document/`
- [X] T050 [US3] Implement `ConnectionsDocumentService` in `be/connection/document/ConnectionsDocumentService.java`:
  - **export** returns the document, its text, the revision and `excludedDanglingConnections`;
  - **preview** validates and diffs;
  - **apply** checks `baseRevision`, re-validates and re-diffs, then in one transaction creates microservices, updates descriptions, adds, updates and deletes connections, and bumps the revision.
- [X] T051 [US3] Implement `ConnectionsDocumentController` (`GET/PUT /api/connections-document`, `POST /api/connections-document/preview`) with the request and response DTOs in `be/connection/document/`. Extend `be/common/RequestSizeLimitFilter.java` to cap `/api/connections-document*` bodies at 2 MB (413), per the research R4 limits
- [X] T052 [US3] Add `useCreateConnection`, `useUpdateConnection`, `useDeleteConnection`, `useReassignConnection`, `useConnectionsDocument`, `usePreviewConnectionsDocument` and `useApplyConnectionsDocument` to `fe/api/queries.ts`
- [X] T053 [P] [US3] Create `fe/canvas/edges/parallelOffsets.ts`, `fe/canvas/edges/bundle.ts` and `fe/canvas/edges/connectionStyle.ts`, with inline styles so they survive image export. Replace `fe/canvas/edges/edgeStyle.ts` (research R11)
- [X] T054 [US3] Create `fe/canvas/edges/ConnectionEdge.tsx`: a quadratic path with a perpendicular offset, the name label (hidden below the overview tier), and click to select. Below the overview tier a bundled `×n` edge zooms in when clicked. Register it in `fe/canvas/edges/edgeTypes.ts` and fold `ProposedEdge.tsx` into `ConnectionEdge`'s proposed variant
- [X] T055 [P] [US3] Create `fe/connections/NewConnectionPrompt.tsx`, opened by `onConnect` and the drop-on-node fallback in `fe/canvas/ArchitectureCanvas.tsx` between two microservices. It asks for a name (required), a kind (none, request or event) and a description, and shows 409 messages inline. A connection from a microservice to itself is blocked
- [X] T056 [P] [US3] Create `fe/connections/ConnectionDetailsPanel.tsx`: name, source, target, kind and description. Rename, change kind or description, and delete
- [X] T057 [P] [US3] Create `fe/connections/DanglingConnectionsPanel.tsx`, which lists `danglingConnections` with **Delete**, **Reassign…** (pick a microservice) and **Recreate "{name}"** (create the microservice)
- [X] T058 [P] [US3] Create `fe/connections/document/ChangePreviewDialog.tsx`. It lists microservices added and changed and connections added, changed and removed, highlights the removed count (also for an empty document), shows "No changes" when `unchanged`, and has Confirm and Cancel
- [X] T059 [US3] Create `fe/connections/document/ConnectionsDocumentEditor.tsx` with CodeMirror (JSON highlighting, local lint, inline markers from `DocumentError.line`/`column`). The buffer is in memory only. Save does preview → dialog → apply with `baseRevision`; on 409 the editor offers to reload. Discard, and the unsaved-changes prompt on close (research R12)
- [X] T060 [US3] Create `fe/connections/document/importExport.ts` and the toolbar actions **Import connections** (file → preview → apply, passing the preview's `revision` as `baseRevision` so changes made in between return 409 instead of being replaced), **Edit connections** (opens the editor) and **Export connections** (downloads `connections.json` from the export `text`, warning when `excludedDanglingConnections > 0`), wired in `fe/App.tsx`
- [X] T061 [US3] Update `fe/canvas/Legend.tsx` to show the request, event and no-kind line styles (FR-018b)

**Checkpoint**: The MVP (US1–US3) is complete. Cards, microservices, links and declared connections
work through both the canvas and the document.

---

## Phase 6: User Story 4 - Search spec cards and highlight a microservice's neighbours (Priority: P2)

**Goal**: Search fields, endpoint paths and operation IDs across cards, with each result's
linked microservice. Clicking a microservice highlights its one-hop connection neighbours.

**Independent Test**: Searching a field present in two linked cards and one unlinked card
highlights exactly those three cards and the two linked microservices. Clicking a microservice
with one upstream and one downstream connection keeps only those three microservices, their connections
and their docked cards emphasized.

- [X] T062 [P] [US4] Update `ft/canvas/fieldSearch.test.ts` and `ft/canvas/blastRadius.test.ts`. Include a timing check: a search over 100 generated cards of 20 endpoints each returns in under 1 s (SC-006). Search matches field names, endpoint paths and operation IDs case-insensitively and partially, and each result carries its card, endpoint and linked microservice or "unlinked". Blast radius uses connections, includes docked cards, and counts several connections per pair once per neighbour
- [X] T063 [US4] Update `fe/canvas/search/fieldSearch.ts` and `fe/canvas/search/SearchBar.tsx`: matches over `SpecCardNodeData.endpoints`, results grouped by card with their microservice, and selecting a result focuses the card and expands the matching endpoint (FR-030 – FR-032)
- [X] T064 [US4] Update `fe/canvas/highlight/blastRadius.ts`, `fe/canvas/highlight/highlightState.ts` and `fe/canvas/HighlightSummary.tsx` for connection neighbours, docked cards and clearing with Esc (FR-033)

**Checkpoint**: US4 works on top of US1–US3.

---

## Phase 7: User Story 5 - Plan changes in Draft Scenario Mode (Priority: P2)

**Goal**: Scenario content v2 adds proposed microservices (optionally with a proposed card),
links, connections, endpoints and fields, proposed removals and proposed connection changes. It is saved
and exported separately from the baseline, and v1 scenarios still import.

**Independent Test**: Add a proposed microservice and a proposed event connection, mark one baseline
connection for removal, change another connection's kind, propose a link and a nested field, and save.
Leaving draft mode shows the baseline unchanged. Exporting and re-importing gives the same
scenario. Exporting the connections document with the scenario applied reflects every change.

### Tests for User Story 5

- [X] T065 [P] [US5] Rework `bt/it/ScenarioIT.java` for v2 content. It covers:
  - CRUD and optimistic locking;
  - validation of `proposedLinks` (data-model.md §7) against the 1:1 rules and `removals.links`;
  - uniqueness of proposed connections across baseline and proposals;
  - name clashes between proposed and baseline microservices;
  - orphans for missing msId, cardId, endpointKey and connectionId (`usedBy`, e.g. `removals.connections[0]`);
  - card-hash drift in `baselineDrift`;
  - export as v2, and importing v1 and v2 documents with `conversionNotes`.

  It must assert the baseline tables are unchanged after every scenario write (FR-039).
- [X] T066 [P] [US5] Unit test `bt/scenario/legacy/LegacyScenarioConverterTest.java`. It covers:
  - `proposedServices` → `proposedMicroservices` with a card;
  - `addedEndpoints` and `addedFields` re-keyed from `serviceKey` to `cardId`;
  - `proposedEdges` → `proposedConnections` named after their label or "A → B", with handles dropped and noted;
  - positions re-keyed;
  - unknown service keys kept as orphans and noted.
- [X] T067 [P] [US5] Update `ft/scenario/overlay.test.ts` and `ft/scenario/scenarioStore.test.ts`:
  - proposed links dock cards;
  - removals mark connections and microservices, and a microservice removal implies its connections and link;
  - connection changes show old → new;
  - nested fields still work (002 T108);
  - the baseline objects are never mutated.

  Add `ft/scenario/applyScenarioToDocument.test.ts` for FR-043.

### Implementation for User Story 5

- [X] T068 [US5] Replace `be/scenario/model/` with the v2 records `ScenarioContent`, `ProposedMicroservice`, `ProposedLink`, `ProposedConnection`, `AddedEndpoint`, `AddedField`, `ScenarioRemovals`, `ConnectionChange` and `XYPosition`. They map data-model.md §7 `SavedScenario.content.proposedMicroservices`, `SavedScenario.content.proposedLinks`, `SavedScenario.content.proposedConnections`, `SavedScenario.content.addedEndpoints`, `SavedScenario.content.addedFields`, `SavedScenario.content.removals`, `SavedScenario.content.connectionChanges` and `SavedScenario.content.positions`, as in the contract. Every list defaults to empty when null
- [X] T069 [US5] Rewrite `be/scenario/ScenarioValidator.java` for the data-model.md §7 rules: `SavedScenario.content.proposedMicroservices` names; `SavedScenario.content.proposedLinks` 1:1 rules, including `SavedScenario.content.removals` (`links`); `SavedScenario.content.proposedConnections` uniqueness and self-connection rules from §3; `SavedScenario.content.connectionChanges` name uniqueness and `kind ∈ {REQUEST, EVENT, NONE}`; and orphan detection. Update data-model.md §7 `SavedScenario.baselineRef` to `{cards, revision}` and drift detection in `be/scenario/ScenarioService.java`
- [X] T070 [US5] Create `be/scenario/legacy/` (the v1 records copied from 002's model, and `LegacyScenarioConverter`). Make `ScenarioController` import accept `formatVersion` 1 or 2 and return `ScenarioImported.conversionNotes`. Export writes `formatVersion: 2`. Files: `be/scenario/ScenarioController.java` and `be/scenario/dto/`
- [X] T071 [US5] Update `fe/scenario/scenarioStore.ts` with actions for:
  - add and remove proposed microservices, with an optional proposed card and endpoints;
  - proposed links;
  - proposed connections, with name, kind and description;
  - toggling the proposed removal of a baseline connection, microservice or link;
  - setting and clearing connection changes;
  - nested added fields, keyed by `cardId` (kept from 002).
- [X] T072 [US5] Rewrite `fe/scenario/overlay.ts` as baseline ⊕ v2 draft:
  - proposed microservice nodes, with proposed cards docked;
  - proposed links re-parenting cards;
  - proposed connections as `ConnectionEdge` with `proposed: true`;
  - removals flagged with `removed: true`, including the derived connections and link of a removed microservice;
  - changes flagged with `{ before, after }`;
  - orphans flagged.

  It must stay pure and memoized (002/R10).
- [X] T073 [P] [US5] Add the proposed, proposed-removal (struck through, faded red) and proposed-change (`old → new`) variants to `fe/canvas/edges/ConnectionEdge.tsx` and `fe/canvas/edges/connectionStyle.ts`, and the matching node styles to `fe/canvas/nodes/MicroserviceNode.tsx`, `fe/canvas/nodes/SpecCardNode.tsx` and `fe/styles/app.css`. Add these styles to `fe/canvas/Legend.tsx` in draft mode (FR-038, FR-037a)
- [X] T074 [US5] Update the draft-mode UI:
  - rework `fe/scenario/ProposedServiceForm.tsx` into `ProposedMicroserviceForm.tsx` (name, description, optional card with endpoints);
  - in draft mode, `NewConnectionPrompt` creates proposed connections;
  - `ConnectionDetailsPanel` and the microservice menu offer **Propose removal** / **Undo** and **Propose change**;
  - drag-to-link creates a proposed link.

  Files: `fe/scenario/`, `fe/connections/` and `fe/canvas/ArchitectureCanvas.tsx`.
- [X] T075 [US5] Create `fe/scenario/applyScenarioToDocument.ts`, and in draft mode offer **Export connections → Baseline only | With scenario applied** (FR-043). Update `fe/scenario/export/exportJson.ts` to v2 and `fe/scenario/ScenarioList.tsx` to show the import `conversionNotes`

**Checkpoint**: US5 works. Drafts propose additions, removals and changes without touching the
baseline.

---

## Phase 8: User Story 6 - Migrate existing data from the previous version (Priority: P3)

**Goal**: One-time, atomic conversion of 002 data (services, manual and confirmed edges,
positions, scenarios) into the new model, with a report shown once in the UI.

**Independent Test**: Seed a database at V3 with 3 services, one confirmed edge, one manual
edge, dismissals and 2 scenarios. After migrating: 3 linked microservices, exactly 2 connections, the
same positions, both scenarios, and a report with counts and notes. A restart does nothing.

### Tests for User Story 6

- [X] T076 [P] [US6] Create 002 seed data in `backend/src/test/resources/legacy-002/`. `seed.sql` holds:
  - 3 services, two sharing a display name;
  - 1 `CONFIRMED`, 1 `MANUAL`, 1 soft-deleted `MANUAL` and 2 `DISMISSED` overrides;
  - 2 endpoint-level manual edges between the same pair;
  - a layout;
  - two v1 scenarios, one with a proposed edge to a deleted service.
- [X] T077 [P] [US6] Integration test `bt/it/Migration002IT.java`, on PostgreSQL through Testcontainers and on H2. It:
  1. runs Flyway to target V3;
  2. loads `legacy-002/seed.sql`;
  3. migrates to the latest version.

  It then asserts:
  - the data-model.md §8 `MigrationReport.counts` and `MigrationReport.notes`;
  - microservices named after the display names, with "Name (2)" for the clash, each linked to a card whose `SpecCard.id` equals the old `service_spec.id`;
  - endpoint keys re-keyed;
  - connections named after the note or "A → B", with endpoint suffixes on collisions, and `Connection.kind` null;
  - positions moved to `ms:{id}`;
  - scenarios converted to v2, with orphans kept;
  - `legacy_*` tables present;
  - a second Flyway run as a no-op (SC-008, FR-046).

### Implementation for User Story 6

- [X] T078 [US6] Implement `V5__Migrate002Data` as a Spring `JavaMigration` bean in `be/migration/V5__Migrate002Data.java`. It uses plain JDBC with vendor-specific JSON casts (`? FORMAT JSON` on H2, `?::jsonb` on PostgreSQL). It performs:
  - services → `spec_card` (keeping the ID, re-keying endpoints) and `microservice` (unique names with ` (n)` suffixes);
  - `CONFIRMED`/`MANUAL` live overrides → `connection`, named per research R10;
  - layout keys → `ms:{id}`;
  - scenarios through `LegacyScenarioConverter`, which is reused from T070;
  - the `migration_report` row (data-model.md §8 `MigrationReport.ranAt`, `MigrationReport.counts`, `MigrationReport.notes`, `MigrationReport.acknowledged`).

  It must skip name collisions with rows already created by 003 development databases, by suffixing names.
- [X] T079 [US6] Write Flyway `V6__rename_legacy_tables.sql` in both vendor folders, renaming `service_spec`, `edge_override` and `inference_settings` to `legacy_*` (data-model.md §9)
- [X] T080 [US6] Create the `MigrationReport` entity, repository and controller in `be/migration/` (`GET /api/migration-report`, which returns 404 when absent, and `POST /api/migration-report/acknowledge`)
- [X] T081 [P] [US6] Create `fe/migration/MigrationSummaryDialog.tsx` and its query hooks in `fe/api/queries.ts`. It is shown once on load while `acknowledged=false`, displays the counts and notes, and states that inferred edges are no longer shown. Closing it acknowledges the report
- [X] T082 [US6] With the user's go-ahead only: back up `backend/data` to `backend/data.backup-002` (copy, never move), start the dev profile against the user's database, and verify quickstart V6. Report the migration summary to the user

**Checkpoint**: All stories are complete. Existing 002 data has been carried over.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T083 [P] Rewrite `ft/e2e/quickstart.spec.ts` as `ft/e2e/declared-connections.spec.ts`, which covers: import card → create microservice → link → upload `shop.json` → draw a connection → export → re-import ("No changes") → draft a removal → leave draft mode with the baseline unchanged. Update `frontend/playwright.config.ts` if the file name changes
- [X] T084 [P] Add the fixture `backend/src/test/resources/specs/oas30/large-200-endpoints.yaml`, and update `bt/it/PerformanceSmokeIT.java` so importing it takes under 30 s (SC-001). Also seed 100 generated cards and microservices and 500 connections. `GET /api/canvas` must stay under 300 ms; also include the SC-005 document timing
- [X] T085 [P] Update `ft/canvas/largeGraph.test.tsx` for 100 microservices, 100 cards and 500 connections: render, offsets and bundling, with no exceptions
- [X] T086 [P] Document the connections document format with an example in `docs/connections-document.md`, linking `contracts/connections-document.schema.json` (FR-029), and serve the schema as a static resource at `/connections-document.schema.json` for editor tooling
- [X] T087 [P] Update `README.md`: the new model (cards, microservices, connections), the connections document, the migration note with the backup step, and the removal of inference
- [X] T088 Remove the Sync Impact Report comment from `.specify/memory/constitution.md` before committing (constitution workflow)
- [X] T089 Run every merge gate in quickstart.md (`./mvnw verify`, `npm run typecheck`, `npm run lint`, `npm test`, `npm run e2e`) and the quickstart V1–V5 checks on the `scratch` profile. Record the results in `specs/003-declared-connections/research.md` under an "Implementation verification" section

---

## Phase 10: Flows (colors) — follow-up (FR-049 – FR-053)

**Goal**: Named color flows that connections can belong to, shown in the legend and highlightable, round-tripped
through the connections document and usable in drafts.

- [X] T090 Write Flyway `V7__flows.sql` (both vendors) creating `flow` (data-model.md §3b `Flow.name`, `Flow.nameNormalized` unique, `Flow.color` CHECK) and adding `connection.flow_id` (FK `ON DELETE SET NULL`); add the `Flow` entity, `FlowColor` enum and repository in `be/connection/`
- [X] T091 Implement `FlowService` and `FlowController` (`POST /api/flows`, `PATCH/DELETE /api/flows/{flowId}`), accept `flowId` in `ConnectionCreate`/`ConnectionPatch`, and add `flows` to `CanvasResponse` and `flowId` to `ConnectionEdgeData` and `Connection`; update contracts/openapi.yaml and regenerate the frontend types
- [X] T092 Extend the connections document (reader, diff, writer, service; data-model.md §3b): `flows[]`, `connections[].flow`, errors `UNKNOWN_FLOW`, `INVALID_COLOR`, `DUPLICATE_FLOW`, flow creation/recolor on apply, export of flows; update contracts/connections-document.schema.json, the served copy and docs/connections-document.md
- [X] T093 Scenario v2: `ProposedConnection.flowId` and `ConnectionChange.flowId` (`NONE` ungroups), with orphan checks in `be/scenario/ScenarioValidator.java`
- [X] T094 Tests: `FlowIT` (CRUD, unique names, delete ungroups, revision bump), validator/diff/writer unit tests for flows, `ConnectionsDocumentIT` round trip with flows, `ScenarioIT` flow orphans
- [X] T095 Frontend: flow color on connections (`connectionStyle`), flow select in `NewConnectionPrompt` and `ConnectionDetailsPanel` (and as a proposed change in draft mode), a Flows side panel (create, rename, recolor, delete), legend entries per flow that highlight the flow's connections when clicked
- [X] T096 Frontend tests (`connectionStyle` flow colors, flow highlight, overlay flow changes) and an e2e step; run every merge gate

## Phase 11: Connection endpoint (API) — follow-up (FR-054 – FR-056)

- [X] T097 Flyway `V8__connection_endpoint.sql` adding `connection.endpoint` (data-model.md §3c `Connection.endpoint`); `ConnectionEndpoints` parse/normalize helper; accept `endpoint` in `ConnectionCreate`/`ConnectionPatch`; expose it on `Connection` and `ConnectionEdgeData` with the computed `endpointMissing`; update contracts/openapi.yaml and regenerate the frontend types
- [X] T098 Connections document: `connections[].endpoint`, error `INVALID_ENDPOINT`, endpoint changes in the diff, export; update the schema, its served copy and docs/connections-document.md
- [X] T099 Scenario v2: `ProposedConnection.endpoint`, `ConnectionChange.endpoint` (`NONE` clears)
- [X] T100 Backend tests: `ConnectionIT` endpoint create/patch/clear/validation and `endpointMissing` after a re-sync; validator, diff and writer unit tests; document round trip with endpoints
- [X] T101 Frontend: endpoint select (target card's endpoints, plus a missing current value) in `NewConnectionPrompt` and `ConnectionDetailsPanel` (proposed change in draft mode), endpoint line and ⚠ on the connection label, overlay and scenario export support
- [X] T102 Frontend tests and an e2e step; run every merge gate

## Phase 12: Terminology — connections and flows; explicit endpoints (follow-up)

- [X] T103 Rename across code, API, contracts, docs and tests: what was called a flow is a **connection**, and what was called a group is a **flow** (`/api/connections`, `/api/flows`, `/api/connections-document`; Java package `connection`; frontend `connections/`)
- [X] T104 Flyway `V9__rename_connections_and_flows.sql` (tables `flow` → `connection`, `flow_group` → `flow`, column `group_id` → `flow_id`) and Java `V10__RenameScenarioKeys` (saved scenario keys and the migration report count)
- [X] T105 Connections document `formatVersion` 2 with version 1 still readable (old property names, positions unchanged); scenario documents `formatVersion` 3 with version 2 converted on import; tests for both
- [X] T106 Endpoint badge near the arrowhead (method badge plus path; method only when zoomed out; ⚠ when missing; proposed and removal styles), with unit and e2e checks
- [X] T107 Fix: a card collapsed to its "API" badge is sent as a hidden node instead of being omitted, so its endpoints stay known (no false ⚠ in the connection panel; search can still find it); the endpoint picker compares endpoints like the backend (`/orders/{id}` = `/orders/{orderId}`); regression tests
- [X] T108 Fix: a proposed connection in a flow keeps the flow's color, with a green halo marking it as proposed (FR-050); the legend explains it; regression tests
- [X] T109 Fix: proposed microservices can be edited after creation (name and description) from their ⋯ menu and the Proposals panel (FR-037b); regression tests

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup and **blocks every story**. T006 and T017
  remove the 002 edge model, so nothing runs end to end until Phase 2 is done.
- **US1 (P1)**: depends on Phase 2.
- **US2 (P1)**: depends on Phase 2. The linking UI (T035) needs US1's cards to be useful, but
  the backend link tests can use cards created directly.
- **US3 (P1)**: depends on Phase 2. Microservices are needed: the document creates them
  itself, and drawing needs US2's `MicroserviceNode` (T033).
- **US4 (P2)**: depends on US1 (cards) and US3 (connections, for blast radius).
- **US5 (P2)**: depends on US1–US3 (it proposes over cards, links and connections).
- **US6 (P3)**: depends on the US1–US3 tables and on `LegacyScenarioConverter` from US5 (T070).
- **Polish**: depends on every story it covers.

### Within Each Story

Tests are written first and must fail before implementation. Then come entities, services,
controllers, query hooks and UI. Tasks touching `fe/canvas/ArchitectureCanvas.tsx`,
`fe/App.tsx` or `fe/api/queries.ts` run sequentially across stories.

### Parallel Opportunities

- **Setup**: T002, T003 and T004 can run in parallel.
- **Foundational**: T007–T012 are separate files and can run in parallel after T005 and T006.
  T015 can run in parallel with T016.
- **US1**: tests T019–T021 can run in parallel; then T025 in parallel with T022/T023.
- **US2**: tests T028 and T029 in parallel; then T033 and T034 in parallel with T030/T031.
- **US3**: tests T037–T044 in parallel. T047 and T053 can start immediately. The UI pieces
  T055–T058 can run in parallel once T052 and T054 are done.
- **US5**: tests T065–T067 in parallel; T073 in parallel with T071/T072.
- **US6**: T076 and T077 in parallel; T081 in parallel with T078–T080.
- **Polish**: T083–T087 in parallel.

### Parallel Example: User Story 3

```text
# Tests first, all at once:
T037 JsonLocatorTest   T038 ConnectionsDocumentValidatorTest   T039 ConnectionsDocumentDiffTest
T040 ConnectionsDocumentWriterTest   T041 ConnectionIT   T042 ConnectionsDocumentIT   T043 connectionEdges.test   T044 editor test
# Independent building blocks:
T047 JsonLocator   T053 parallelOffsets/bundle/connectionStyle
# After T052 + T054:
T055 NewConnectionPrompt   T056 ConnectionDetailsPanel   T057 DanglingConnectionsPanel   T058 ChangePreviewDialog
```

---

## Implementation Strategy

### MVP First (US1 + US2 + US3, all P1)

1. Phase 1 and Phase 2. This removes inference, so the app shows an empty canvas.
2. US1 (cards), then US2 (microservices and links), then US3 (connections and the document).
3. **Stop and validate**: run quickstart V1–V3 on the `scratch` profile. This is the
   smallest version in which the new model is useful in a meeting.

### Incremental Delivery

4. US4 (search and highlight), then quickstart V4.
5. US5 (draft scenarios v2), then quickstart V5.
6. US6 (migration). Only then point the backend at the user's real `backend/data`, after a
   backup and with the user's confirmation (T082), and run quickstart V6.
7. Polish, then run every merge gate.

### Notes

- [P] means different files and no dependency on unfinished tasks.
- Commit after each task or logical flow, but only when the user asks.
- Never run the dev backend against `backend/data` before T082.
