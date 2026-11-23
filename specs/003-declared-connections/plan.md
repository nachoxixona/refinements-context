# Implementation Plan: Declared Connections & Spec Cards

**Branch**: `003-declared-connections` | **Date**: 2026-11-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-declared-connections/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

This feature reworks the running 002 system around **declared connections**, as required by
constitution v2.0.0. Edge inference is removed completely.

The Java 21 / Spring Boot 3.5 **backend**:

- keeps the tested ingestion pipeline (swagger-parser, `$ref` staging, the field flattener);
- stores each imported spec as an unlinked **SpecCard** that never creates connections;
- adds first-class **Microservice** rows, with unique names and at most one linked card;
- adds **Connection** rows: named, optionally `request`/`event`, any number per pair, unique per
  source → target;
- serves a **connections document**: a JSON format with an optional microservices list, plus
  validate, diff and preview endpoints, and an all-or-nothing replace-apply guarded by a
  workspace revision;
- validates documents as **text**, so every error carries a JSON path, a line and a column;
- converts existing 002 data once, in a Flyway Java migration, and records a report.

The React 18 + TypeScript **frontend**:

- splits the old service node into `MicroserviceNode` and `SpecCardNode`. A linked card is a
  React Connection child node docked under its microservice, collapsible to a badge;
- draws connections with `ConnectionEdge`: line style by kind, perpendicular offsets for parallel connections,
  and bundling when zoomed out;
- adds a CodeMirror connections-document editor with inline error markers, a change preview dialog,
  a dangling-connections panel and the migration summary;
- extends Draft Scenario Mode with proposed links, proposed connections, proposed removals and
  proposed connection changes (scenario content v2, with v1 import).

## Technical Context

**Language/Version**: Java 21 (backend). TypeScript 5.x in `strict` mode on Node.js 22 LTS
(frontend tooling). Both are unchanged from 002.

**Primary Dependencies**:

- **Backend**: unchanged from 002: Spring Boot 3.5.x, `io.swagger.parser.v3:swagger-parser`
  2.1.x, Flyway, springdoc-openapi 2.8.x, Jackson (its streaming API is used for document
  positions).
- **Frontend**: 002's stack (React 18.3, Vite, `@xyflow/react` 12.x, `elkjs` 0.10.x, TanStack
  Query 5, Zustand 5, `html-to-image`, `openapi-typescript`) plus `@uiw/react-codemirror`
  ^4.23, `@codemirror/lang-json` ^6 and `@codemirror/lint` ^6.8
  ([R12](./research.md#r12-connections-document-editor), [R15](./research.md#r15-versions)).

**Storage**: PostgreSQL 16 (production) and H2 file database (dev), as in 002. Flyway adds:

- `V4`: the new tables;
- `V5`: the Java data migration;
- `V6`: renames the old tables to `legacy_*`.

See [data-model.md](./data-model.md) and [R10](./research.md#r10-migrating-002-data-one-time).

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers (PostgreSQL), Vitest, React Testing
Library and Playwright. The suite changes are listed in [R14](./research.md#r14-testing-strategy-changes-to-002r16).

**Target Platform**: unchanged: a JVM on Linux or a laptop, and desktop evergreen browsers over
screen share.

**Project Type**: Web application (`backend/` and `frontend/`), with the existing
repository layout.

**Performance Goals**:

- A card appears in under 30 s for 200 endpoints (SC-001).
- A 200-connection / 100-microservice document is validated, previewed and applied in under 5 s
  (SC-005).
- Search over 100 cards answers in under 1 s (SC-006).
- Pan and zoom stay smooth with 100 microservices, 100 cards and 500 connections.

**Constraints**:

- Specs are at most 10 MB, and connections documents at most 2 MB, with at most 5,000 connections and
  2,000 microservices.
- 002's URL-fetch and `$ref` restrictions are unchanged.
- There is no spec parsing in the browser.
- A scenario never writes to the baseline.
- The editor save is guarded by the workspace revision, and the layout and scenarios by
  optimistic locks.
- There is no authentication (single editor, as in 002).

**Scale/Scope**: about 100 microservices, 100 cards, up to 500 connections and tens of scenarios.
One workspace and one concurrent editor.

There are no `NEEDS CLARIFICATION` items. The five spec clarifications plus
[research.md](./research.md) R1–R15 settle every unknown.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.* The check is against
constitution **v2.0.0**.

| # | Principle / rule | How the plan complies | Pre-research | Post-design |
|---|------------------|-----------------------|:---:|:---:|
| I | **Deterministic parsing → Spec Cards; no connections from specs; no LLMs; errors with location** | The 002 SpecParser and FieldFlattener are reused unchanged, and import produces a `SpecCard` only. The `card` package does not depend on the `connection` package, and `SpecCardIT` asserts the connection count is unchanged on import, sync and delete (FR-007, SC-007) | ✅ | ✅ |
| II | **Declared connections**: microservices without specs, at most one card, explicit linking, microservice → microservice connections only, declared by the document or drawn, document upload/edit/export with round trip, backend validation with entry positions, no partial apply, connections independent of specs, dangling flagged not deleted | Data-model §2–§4. The unique `specCardId` and the explicit link API with `replace`. Connection FKs use `ON DELETE SET NULL` and keep names, which makes dangling connections. Text-based validation with positions (R6). Replace-apply in one transaction (R5). Deterministic export plus an empty diff on re-import (SC-003). There is no inference code left (R1) | ✅ | ✅ |
| III | **Readable layout**: on-demand hierarchical layout covering microservices and cards with linked cards attached; dragged positions saved and taking precedence | ELK runs only on **Auto-arrange**, with a microservice and its docked card as one block (R8). Positions are keyed `ms:`/`card:` with debounced saves (002/R11). Docked cards are React Connection child nodes | ✅ | ✅ |
| IV | **Scenario sandbox**: proposed microservices, links, connections, endpoints and fields; separate storage; distinct styling; export and share; promotion only when explicit | Scenario content v2 (R9, data-model §7) adds proposed links, connections, removals and connection changes. It is stored only in `saved_scenario`, and the overlay is pure (002/R10). Proposed and removal styling (R11). JSON v2 and PNG export, and v1 import. No promotion in this version | ✅ | ✅ |
| V | **Backend standards**: Java 21, Boot 3.x, React Connection DTOs, H2 and PostgreSQL with versioned migrations, JUnit for multi-spec parsing **and** the connections document (validation, import, export, round trip) | `CanvasNode` and `CanvasEdge` use the React Connection shape, including `parentId` (contract). Flyway V4–V6. The 002 parsing suites are kept, and `ConnectionsDocumentValidatorTest`, `ConnectionsDocumentDiffTest`, `ConnectionsDocumentWriterTest` and `ConnectionsDocumentIT` are added (R14) | ✅ | ✅ |
| V | **Frontend standards**: TS strict, `@xyflow/react`, microservice nodes and cards visually distinct, cards zoom-adaptive, no spec parsing in the browser, JSON aids allowed with the backend authoritative | `MicroserviceNode` and `SpecCardNode` are separate components with separate styles. `SpecCardNode` keeps the 002 zoom tiers. CodeMirror performs only JSON well-formedness checks; every save goes through backend preview and apply (R12) | ✅ | ✅ |
| C1 | **Single source of truth** | Unsaved editor text, unsaved drags and draft scenarios are kept in memory only, all of which count as transient state. Nothing goes to `localStorage` | ✅ | ✅ |
| C2 | **Spec traceability**: card, endpoint and field reference the spec upload | `SpecCardNodeData` carries `contentHash`, `sourceType`/`sourceUrl` and `lastSyncAt`. Endpoint keys are prefixed with the card ID | ✅ | ✅ |
| C3 | **Idempotent ingestion**: no duplicate cards; importing the same document twice duplicates nothing | `contentHash`/`sourceUrl` duplicate detection (409). Document application is keyed by name, so a second identical apply yields `unchanged: true` | ✅ | ✅ |
| C4 | **API contracts** | [contracts/openapi.yaml](./contracts/openapi.yaml) is v2.0.0. Breaking removals are coordinated in one change set: the regenerated TS types and `ContractConformanceIT` | ✅ | ✅ |
| C5 | **Security baseline**: untrusted specs **and documents**, restricted remote refs, size limits | 002's spec protections are unchanged. The connections document has a 2 MB cap enforced by `RequestSizeLimitFilter`, count limits, streaming parsing and no expression evaluation | ✅ | ✅ |
| W | **Gates**: parsing and connections-document suites pass; strict type check; format fixtures for valid round trips and precise rejections; edge-model tests prove connections survive re-import, unlinking and scenario edits | The merge gates are in [quickstart.md](./quickstart.md#automated-checks-merge-gates). The `backend/src/test/resources/connections/` fixtures cover valid, invalid-per-kind and large documents. `SpecCardIT`, `MicroserviceLinkIT` and `ScenarioIT` cover survival | ✅ | ✅ |
| G | **Migration from 1.x**: services → linked microservices and cards; manual and confirmed edges → connections; inferred edges, dismissals and settings discarded; layout and scenarios kept or reported | `V5__Migrate002Data` and `LegacyScenarioConverter` (R10). `migration_report` and its UI. `Migration002IT` (SC-008). The old tables are renamed, not dropped | ✅ | ✅ |

**Gate result: PASS.** There are no violations, so Complexity Tracking lists only additions
for review.

One point looks like a possible conflict but isn't:

- **Client-side parallel offsets, bundling, and the "export with scenario applied" transformation.**
  This is presentation logic and pure transformation over backend-provided connections. It makes no
  domain decision. Validation stays on the backend: a document exported with the scenario
  applied is validated as usual when it is uploaded.

## Project Structure

### Documentation (this feature)

```text
specs/003-declared-connections/
├── plan.md                          # This file
├── research.md                      # Phase 0
├── data-model.md                    # Phase 1
├── quickstart.md                    # Phase 1
├── contracts/
│   ├── openapi.yaml                 # REST contract v2.0.0 (replaces 002's 1.x contract)
│   └── connections-document.schema.json   # Connections document v1 wire format
├── checklists/requirements.md       # From /speckit-specify
└── tasks.md                         # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

Paths are under `backend/src/main/java/com/refinements/visualizer/` unless shown otherwise.
Legend: **new**, *changed*, ~~removed~~.

```text
backend/
├── src/main/java/com/refinements/visualizer/
│   ├── common/                 # *ApiExceptionHandler (ConflictProblem, ConnectionsDocumentProblem)*,
│   │                           #   *RequestSizeLimitFilter (2 MB for /connections-document)*, JacksonConfig…
│   ├── ingestion/              # SpecFetcher, SpecParser, FieldFlattener (unchanged), FieldNormalizer
│   │                           #   (moved from inference/), *SpecKeys (from ServiceKeys: endpointKey,
│   │                           #   normalizePath, sha256Hex)*
│   ├── card/                   # **SpecCard entity + repo, SpecCardService (import/sync/delete),
│   │                           #   SpecCardController, dto/**   (replaces ~~service/~~)
│   ├── microservice/           # **Microservice entity + repo, MicroserviceService (create/rename/
│   │                           #   delete/link/unlink, dangling re-attach), MicroserviceController**
│   ├── connection/                   # **Connection entity + repo, ConnectionService, ConnectionController, Names (norm)**
│   │   └── document/           # **JsonLocator, ConnectionsDocumentReader (text → model + errors),
│   │                           #   ConnectionsDocumentValidator, ConnectionsDocumentDiff, ConnectionsDocumentWriter,
│   │                           #   ConnectionsDocumentService (preview/apply), ConnectionsDocumentController**
│   ├── workspace/              # **WorkspaceState entity, WorkspaceRevision (bump/check)**
│   ├── canvas/                 # **CanvasService (nodes incl. parentId, edges, dangling), CanvasController,
│   │                           #   dto/**   (replaces ~~graph/~~)
│   ├── layout/                 # *BaselineLayout (+collapsedCards), LayoutController*
│   ├── scenario/               # *model v2, ScenarioValidator (v2 rules, orphans), ScenarioService,
│   │   │                       #   ScenarioController (import v1|v2)*
│   │   └── legacy/             # **V1 records + LegacyScenarioConverter**
│   ├── migration/              # **V5__Migrate002Data (JavaMigration bean), MigrationReport entity,
│   │                           #   MigrationReportController**
│   ├── ~~inference/~~          # removed (EdgeInferenceEngine, ConfidenceScorer, InferenceSettings…)
│   └── ~~edge/~~               # removed (EdgeOverride…)
├── src/main/resources/db/migration/{postgresql,h2}/
│   ├── V4__cards_microservices_connections.sql      # **new tables**
│   └── V6__rename_legacy_tables.sql           # **legacy_* rename**
└── src/test/
    ├── java/…/card/, microservice/, connection/document/, scenario/legacy/, canvas/   # **unit tests (R14)**
    ├── java/…/it/              # **SpecCardIT, MicroserviceLinkIT, ConnectionIT, ConnectionsDocumentIT,
    │                           #   Migration002IT**, *ScenarioIT, PerformanceSmokeIT, ContractConformanceIT*
    └── resources/
        ├── specs/              # unchanged fixtures (reference-workspace/expected-edges.json ~~removed~~)
        ├── connections/              # **shop.json, invalid-*.json, large-200.json, roundtrip/*.json**
        └── legacy-002/         # **seed SQL + v1 scenario exports for Migration002IT**

frontend/src/
├── api/                        # *schema.d.ts regenerated, client.ts, queries.ts (cards, microservices,
│                               #   connections, connections-document, canvas, migration)*
├── canvas/
│   ├── ArchitectureCanvas.tsx  # *node/edge types, drop-card-on-microservice linking, connect → name prompt*
│   ├── nodes/MicroserviceNode.tsx      # **name, description, API badge, handles**
│   ├── nodes/SpecCardNode.tsx          # **endpoints/fields/zoom tiers/add-field (moved from CustomServiceNode)**
│   ├── nodes/~~CustomServiceNode.tsx~~
│   ├── edges/ConnectionEdge.tsx, connectionStyle.ts, parallelOffsets.ts, bundle.ts   # **R11**
│   ├── edges/~~InferredEdge, VerifiedEdge~~, *ProposedEdge → proposed style on ConnectionEdge*
│   ├── layout/elkLayout.ts     # *compound microservice+card blocks*
│   ├── highlight/, search/     # *connections-based neighbours; card + microservice results*
│   └── Legend.tsx              # *kinds + proposed/removal styles*
├── cards/ImportSpecDialog.tsx, ResyncButton.tsx          # *(from ingestion/)*
├── microservices/MicroserviceForm.tsx, LinkConflictDialog.tsx   # **new**
├── connections/ConnectionDetailsPanel.tsx, NewConnectionPrompt.tsx, DanglingConnectionsPanel.tsx   # **new**
├── connections/document/ConnectionsDocumentEditor.tsx, ChangePreviewDialog.tsx, importExport.ts  # **new (R12)**
├── migration/MigrationSummaryDialog.tsx                  # **new**
├── scenario/                   # *overlay v2 (links, connections, removals, changes), scenarioStore,
│                               #   applyScenarioToDocument.ts (FR-043), ScenarioToolbar*
├── ~~edges/~~ (EdgeDetailsPanel, DismissedEdgesList), ~~settings/~~ (InferenceSettingsDialog)
└── tests/e2e/declared-connections.spec.ts   # **replaces quickstart.spec.ts connections (R14)**
```

**Structure Decision**: The repository keeps its two projects. The backend stays organized by
domain feature: `card`, `microservice`, `connection` (with a `document` sub-package), `canvas`,
`layout`, `scenario`, `migration` and `workspace`. Each user story therefore maps to one or
two packages:

| User story | Backend packages |
|---|---|
| US1 | `card` |
| US2 | `microservice` |
| US3 | `connection`, `connection/document` |
| US4 | (frontend only) |
| US5 | `scenario` |
| US6 | `migration` |

The frontend mirrors this. 002 code is changed in place rather than copied, so there is never
a second, parallel implementation of parsing, cards or the canvas.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

There are no violations. These additions go beyond the minimum and are recorded for review:

| Addition | Why needed | Simpler alternative rejected because |
|----------|------------|-------------------------------------|
| `WorkspaceState.revision` single-row counter | Stops a stale editor buffer from wiping connections drawn on the canvas in another tab (R7) | Per-connection versions can't detect a connection *added* elsewhere, and replace semantics would silently delete it |
| Denormalized `sourceName`/`targetName` on `Connection` next to nullable FKs | Dangling connections must stay displayable and matchable by name, and recreating by name must re-attach them (R2) | Tombstone microservices complicate the uniqueness rules. Name-only references break scenarios and positions on rename |
| Text-based document submission plus `JsonLocator` | FR-023 and SC-004 require errors that point at the offending entry; line and column feed the editor markers (R6) | JSON Schema validators report schema paths without source positions and can't express the cross-entry rules |
| Flyway Java migration plus `legacy_*` tables kept for one release | The migration must be once-only and atomic, must rewrite JSON scenarios portably, and must be safe for the user's existing data (R10) | A startup runner races with requests. SQL-only JSON rewriting is not portable between H2 and PostgreSQL. Dropping the tables leaves no fallback |
| CodeMirror dependency (~150 KB gzipped) | In-app editing with inline error markers (FR-022, FR-023) | A textarea can't mark lines. Monaco is about 2 MB and awkward with workers |
