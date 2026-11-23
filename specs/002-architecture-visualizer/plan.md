# Implementation Plan: Microservice Architecture & Scenario Visualizer

**Branch**: `002-architecture-visualizer` | **Date**: 2026-11-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-architecture-visualizer/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A single-editor web app that turns microservice OpenAPI/Swagger specs into an interactive
architecture diagram for refinement meetings. The editor shares their screen while the team
watches.

A **Java 21 / Spring Boot 3** backend:

- ingests specs from pasted text or a URL;
- parses and fully `$ref`-resolves them with `io.swagger.parser.v3:swagger-parser`;
- flattens each operation into path-qualified input and output fields;
- persists `ServiceSpec`, `EdgeOverride` and `SavedScenario` in PostgreSQL (H2 for local dev);
- computes **inferred edges** on demand by matching normalized response-field names to request
  inputs in other services, with a configurable generic-field denylist and explainable
  confidence scores;
- returns them from `GET /api/graph` as React Flow–native nodes and edges, merged with human
  overrides (confirmed, dismissed, manual) keyed by stable natural keys, so they survive every
  re-sync.

A **React 18 + TypeScript (strict) + Vite** frontend:

- renders the graph with `@xyflow/react`, using a `CustomServiceNode` (method badges, endpoint
  rows, collapsible fields, per-endpoint handles, zoom-dependent detail);
- lays out with `elkjs` only when asked, while keeping dragged positions;
- does one-hop blast-radius and field-search highlighting over the backend-provided data;
- runs **Draft Scenario Mode** as a client-side overlay (green dashed proposals) that never
  mutates the baseline, saved through `/api/scenarios` and exportable as JSON or PNG.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript 5.x in `strict` mode on Node.js 22 LTS
(frontend tooling)

**Primary Dependencies**:

- **Backend**: Spring Boot 3.5.x (Web, Validation, Data JPA, Actuator),
  `io.swagger.parser.v3:swagger-parser` 2.1.x, Flyway, springdoc-openapi 2.8.x
- **Frontend**: React 18.3, Vite, `@xyflow/react` 12.x, `elkjs` 0.10.x, TanStack Query 5,
  Zustand 5, `html-to-image` 1.11.x, `openapi-typescript` 7

Versions are pinned in [research.md R17](./research.md#r17-versions-pinned-for-the-plan).

**Storage**: PostgreSQL 16 in production and H2 (PostgreSQL mode, file-based) for local dev. JSON
document columns use `jsonb` on PostgreSQL. Migrations use Flyway with vendor-specific folders
([R8](./research.md#r8-persistence-postgresql-in-production-h2-locally-flyway)).

**Testing**:

- **Backend**: JUnit 5, AssertJ, Spring MockMvc, Testcontainers (PostgreSQL)
- **Frontend**: Vitest and React Testing Library, `tsc --noEmit`, plus one Playwright smoke test
  ([R16](./research.md#r16-testing-strategy))

**Target Platform**:

- **Backend**: a Linux server or a developer laptop, run as a JVM process on the internal network
- **Frontend**: desktop evergreen browsers (Chrome, Edge, Firefox, Safari), shown over screen
  share

**Project Type**: Web application (separate `backend/` and `frontend/`)

**Performance Goals**:

- A spec with 100 endpoints parses and appears in under 10 s (SC-002).
- Building the graph takes under 300 ms server-side for 50 services. Highlighting and search
  respond in under 1 s (SC-005).
- Pan, zoom and drag stay smooth with 50 nodes and 500 edges (SC-006).

**Constraints**:

- Specs are at most 10 MB. URL fetches time out after 5 s to connect and 15 s to read.
- Relative `$ref`s on the spec's own host are resolved through `SpecFetcher` limits; refs to other
  hosts are blocked unless allowlisted. There is no schema parsing in the browser.
- Baseline data is never mutated by a scenario.
- Optimistic locking on the layout and on scenarios.
- No auth in v1 (single editor, per the spec's Clarifications).

**Scale/Scope**: About 50 services per workspace, up to 100 endpoints each (and individual
specs with several hundred), about 500 edges, tens of scenarios. There is one workspace and one
concurrent editor.

There are no `NEEDS CLARIFICATION` items left. Every unknown was resolved in
[research.md](./research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle / rule | How the plan complies | Pre-research | Post-design |
|---|------------------|-----------------------|:---:|:---:|
| I | **Spec-First Deterministic Parsing**: swagger-parser on the backend, full `$ref` resolution, Swagger 2.0 converted to the 3.x model, no LLM parsing, errors reported with location | `SpecParser` uses `OpenAPIParser.readContents` with `resolveFully` and the built-in 2.0 converter. URL specs pass their URL as the base location, so file-relative refs resolve (same host by default, through `SpecFetcher`). Failed results are rejected as a whole with `{location, message}` errors (R1). The inference engine is deterministic and sorted (R4) | ✅ | ✅ |
| II | **Inferred vs. verified**: `origin` on every edge, confidence and matched fields, dashed vs. solid, overrides survive re-upload, orphans flagged, promote and dismiss | `EdgeData.origin`, `confidence`, `matches[]`. Natural-key `EdgeOverride` (`CONFIRMED`, `DISMISSED`, `MANUAL`). Orphans are computed when the graph is built and never deleted (R6, R7, data-model §2) | ✅ | ✅ |
| III | **Noise-free layout**: configurable generic denylist (default list included), qualified-name preference, ELK hierarchical layout, dragged positions win, auto-layout only on explicit action | `InferenceSettings.genericFields`, editable through the API and seeded from config (R5). The "entity-qualified" bonus and a fan-out cap (R4). ELK `layered` only on the **Auto-arrange** click. Debounced position saves (R11) | ✅ | ✅ |
| IV | **Scenario sandbox isolation**: separate storage, no baseline mutation, visually distinct, export and share, explicit promotion only | `SavedScenario` holds additions and positions only. The client renders a baseline ⊕ overlay selector and never mutates the server graph (R10). Green dashed styling (R13). JSON and PNG export plus import (R15). Promotion to the baseline is out of scope for v1 (spec) | ✅ | ✅ |
| V | **Backend standards**: Java 21+, Boot 3.x, React Flow–shaped DTOs, H2 dev and PostgreSQL prod, versioned migrations, JUnit 5 for multi-spec parsing and inference | Boot 3.5 on Java 21. `GraphNode` and `GraphEdge` match the React Flow `Node`/`Edge` shape (contracts). Flyway `db/migration/{vendor}`. Parsing and inference fixture suites (R16) | ✅ | ✅ |
| V | **Frontend standards**: React 18+, TS strict, `@xyflow/react`, zoom-adaptive custom nodes, zero business-logic schema parsing in the browser | React 18.3, `strict: true`, `CustomServiceNode` with three zoom tiers (R12). Blast radius and search only filter already-flattened backend fields (R14, a justified non-violation below) | ✅ | ✅ |
| C1 | **Single source of truth**: no local persistence of domain state except transient UI state | Unsaved draft edits live in memory (Zustand) until **Save**, which is transient UI state like unsaved drags. Nothing goes to `localStorage` | ✅ | ✅ |
| C2 | **Spec traceability**: nodes reference their source spec and upload | `ServiceNodeData` carries `specFormat`, `sourceUrl`, `contentHash` (the upload identifier) and `lastSyncAt`. Endpoints and fields are traced through their parent node (data-model §1 Traceability). Scenario `baselineRef` | ✅ | ✅ |
| C3 | **Idempotent ingestion** | `serviceKey` uniqueness, a `contentHash` no-op, and an explicit `mode` on clashes (data-model §1) | ✅ | ✅ |
| C4 | **API contracts**: the backend documents its own API in OpenAPI | [contracts/openapi.yaml](./contracts/openapi.yaml) plus springdoc at runtime. TS types are generated from the contract (R9) | ✅ | ✅ |
| C5 | **Security baseline**: untrusted specs, remote refs restricted, size limits | Same-host relative refs resolved through `SpecFetcher` limits; other hosts blocked unless on the remote-ref allowlist (empty by default); a 10 MB streaming cap, http(s) only, timeouts (R1, R2). Private-network URLs are allowed on purpose, see the note below | ✅ | ✅ |
| W | **Workflow gates**: Spec Kit flow, tests pass, `strict` type check, fixture tests for inference rules and for override survival | Merge gates in [quickstart.md](./quickstart.md#automated-checks-merge-gates). Fixture-based reference workspace with an SC-003 precision assertion | ✅ | ✅ |

**Gate result: PASS.** No violations, so Complexity Tracking is empty.

Two things look like possible conflicts but are not:

- **Client-side blast radius and search (Principle V).** The principle forbids *schema parsing,
  `$ref` resolution and edge inference* in the browser. Highlighting walks edges the backend
  already computed, and searching matches `normalizedName` and `path` values the backend
  already produced. The browser makes no domain decisions.
- **Private-network spec URLs (security baseline).** The constitution requires remote `$ref`
  resolution to be restricted. That is honored. Fetching the **top-level** spec URL from
  internal hosts is the product's purpose. Scheme, size and timeout limits apply, and a host
  allowlist property exists for hardening.

## Project Structure

### Documentation (this feature)

```text
specs/002-architecture-visualizer/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── openapi.yaml     # Phase 1 output: REST contract and React Flow DTO schemas
├── checklists/
│   └── requirements.md  # From /speckit-specify
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                   # Spring Boot 3.5 parent, Java 21
└── src/
    ├── main/
    │   ├── java/com/refinements/visualizer/
    │   │   ├── VisualizerApplication.java
    │   │   ├── common/                       # ProblemDetail handler, config properties, clock
    │   │   ├── ingestion/                    # SpecFetcher (URL), SpecParser (swagger-parser),
    │   │   │                                 #   FieldFlattener, ServiceKeys (natural keys)
    │   │   ├── service/                      # ServiceSpec entity + repo, ServiceController,
    │   │   │                                 #   ServiceRegistrationService (register/sync/delete)
    │   │   ├── inference/                    # EdgeInferenceEngine, FieldNormalizer,
    │   │   │                                 #   ConfidenceScorer, InferenceSettings entity + API
    │   │   ├── edge/                         # EdgeOverride entity + repo, EdgeOverrideController
    │   │   ├── graph/                        # GraphService (merge + orphan + cache),
    │   │   │   └── dto/                      #   GraphController, React Flow DTO records
    │   │   ├── layout/                       # BaselineLayout entity + LayoutController
    │   │   └── scenario/                     # SavedScenario entity + repo, ScenarioController,
    │   │                                     #   ScenarioValidator (orphans, import, drift)
    │   └── resources/
    │       ├── application.yml               # visualizer.* defaults (generic fields, limits)
    │       ├── application-dev.yml           # H2 file DB, PostgreSQL mode
    │       ├── application-prod.yml          # PostgreSQL
    │       └── db/migration/{postgresql,h2}/ # Flyway V1__init.sql, …
    └── test/
        ├── java/com/refinements/visualizer/
        │   ├── ingestion/                    # SpecParserTest, FieldFlattenerTest (fixtures)
        │   ├── inference/                    # EdgeInferenceEngineTest, ReferencePrecisionTest
        │   ├── graph/                        # GraphServiceTest (merge, orphans)
        │   └── it/                           # *IT: MockMvc + Testcontainers, PerformanceSmokeIT
        └── resources/specs/                  # swagger2/, oas30/, oas31/, refs/, invalid/,
                                              #   reference-workspace/

frontend/
├── package.json                              # scripts: dev, build, typecheck, test, e2e, gen:api
├── vite.config.ts                            # /api proxy → :8080
├── tsconfig.json                             # "strict": true
└── src/
    ├── main.tsx, App.tsx
    ├── api/
    │   ├── schema.d.ts                       # generated by openapi-typescript (do not edit)
    │   ├── client.ts                         # typed fetch wrapper, ProblemDetail errors
    │   └── queries.ts                        # TanStack Query hooks
    ├── canvas/
    │   ├── ArchitectureCanvas.tsx            # ReactFlow root, filters, legend, presentation mode
    │   ├── nodes/CustomServiceNode.tsx       # badges, endpoint rows, fields, handles, zoom tiers
    │   ├── nodes/MethodBadge.tsx
    │   ├── edges/InferredEdge.tsx, VerifiedEdge.tsx, ProposedEdge.tsx
    │   ├── layout/elkLayout.ts (+ worker)    # auto-arrange, placement of new nodes
    │   ├── highlight/blastRadius.ts          # one-hop upstream/downstream
    │   ├── search/fieldSearch.ts
    │   └── canvasStore.ts                    # Zustand: selection, highlight, filters, expansion
    ├── ingestion/RegisterServiceDialog.tsx, ResyncButton.tsx
    ├── edges/EdgeDetailsPanel.tsx, DismissedEdgesList.tsx
    ├── scenario/
    │   ├── scenarioStore.ts                  # Zustand: draft overlay, dirty, version
    │   ├── overlay.ts                        # baseline ⊕ draft selector (pure)
    │   ├── ScenarioToolbar.tsx, ProposedServiceForm.tsx, ScenarioList.tsx
    │   └── export/exportJson.ts, exportImage.ts
    └── settings/InferenceSettingsDialog.tsx
└── tests/                                    # Vitest unit tests mirror src/; e2e/ (Playwright)
```

**Structure Decision**: This is a web application with two independently buildable projects,
`backend/` (Maven, Spring Boot) and `frontend/` (npm, Vite), in one repository. The backend is
organized **by domain feature** (ingestion, inference, edge, graph, layout, scenario) rather
than by technical layer, so each user story maps to a small number of packages. The frontend
mirrors this with `canvas/`, `ingestion/`, `edges/` and `scenario/`. `contracts/openapi.yaml`
is the single DTO source shared by both sides through code generation. In development, Vite
proxies `/api` to the backend. For deployment, the frontend build can be served as static
resources by Spring Boot (one artifact) or by any static host.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations. Two design choices go beyond the user's input and are recorded for review:

| Addition | Why needed | Simpler alternative rejected because |
|----------|------------|--------------------------------------|
| `BaselineLayout` and `InferenceSettings` single-row tables besides the three core entities | FR-014 needs saved baseline positions. FR-016 and Principle III need a generic-field list editable without a code change | Storing positions on `ServiceSpec` gets tangled with re-sync writes and optimistic locking. A config-only denylist cannot be edited in the app |
| Extra endpoints besides the four requested (`/services/{key}/sync`, `DELETE /services/{key}`, `/edges/overrides`, `/layout`, `/settings/inference`, `/scenarios/import`, `/scenarios/{id}/export`) | Re-sync (FR-006), remove (FR-009), restoring dismissed edges (edge case), layout persistence (FR-014), denylist editing (FR-016), import and export (FR-035) | Overloading `POST /api/services` and `GET /api/graph` with mode flags would make the contract ambiguous and harder to test |
