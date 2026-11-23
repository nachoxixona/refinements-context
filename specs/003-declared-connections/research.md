# Research: Declared Connections & Spec Cards

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

This feature reworks the running 002 system rather than starting a new code base. Decisions
from [002 research](../002-architecture-visualizer/research.md) that still hold are reused by
reference (`002/Rn`). Everything below is new or changed.

---

## R1. What is kept, changed and removed from 002

**Decision**:

| 002 area | Fate in 003 |
|---|---|
| `ingestion/` (SpecFetcher, SpecParser, FieldFlattener, FieldNormalizer, ServiceKeys) | **Kept** (002/R1–R3). `FieldNormalizer` moves here from `inference/`. `ServiceKeys` becomes `SpecKeys`: it keeps `endpointKey`, `normalizePath` and `sha256Hex`, and drops the service-key and edge-key helpers. The flattener is called with the card ID instead of a service key, so endpoint keys become `{cardId}:{METHOD} {path}` |
| `service/` (ServiceSpec, registration) | **Reworked** into `card/` (SpecCard). Same parsing, sync and limits; no service key or graph role |
| `inference/`, `edge/` | **Removed** (EdgeInferenceEngine, ConfidenceScorer, InferenceSettings, EdgeOverride, their APIs and tests) |
| `graph/` | **Replaced** by `canvas/` (CanvasService builds React Connection nodes and edges from microservices, cards and connections) |
| `layout/` | **Kept**, keys change to `ms:{id}` and `card:{id}`, plus `collapsedCards` |
| `scenario/` | **Reworked**: content format v2 (R9), with v1 import through a converter |
| Frontend `CustomServiceNode` | Split into `MicroserviceNode` and `SpecCardNode`. The endpoint, field and add-field rendering (including nested proposed fields) moves into `SpecCardNode` unchanged |
| Frontend inferred/verified edges, EdgeDetailsPanel, DismissedEdgesList, InferenceSettingsDialog | **Removed**; replaced by `ConnectionEdge`, `ConnectionDetailsPanel`, `DanglingConnectionsPanel` |
| Search, blast radius, ELK layout, export (JSON, PNG), presentation mode | **Kept**, adapted to the new node types |

**Rationale**: Parsing, card rendering, layout and scenarios are most of the work and are
already tested. Only the edge model changes fundamentally.

**Alternatives considered**: A fresh code base. It was rejected because it would throw away
the tested parser, flattener and canvas for no benefit.

## R2. Microservice identity and how connections refer to microservices

**Decision**:

- Each microservice has a UUID `id`. The ID is stable across renames, and scenarios and the
  layout use it.
- Each microservice also has a unique `nameNormalized`, which is `lower(trim(collapse-spaces(name)))`.
- A connection stores `sourceId` and `targetId` as nullable foreign keys with `ON DELETE SET NULL`,
  **plus** `sourceName` and `targetName` (with normalized copies).
  - The names are kept equal to the current microservice name. A rename updates them in the
    same transaction (FR-028).
  - When a microservice is deleted, the ID becomes null and the name remains. That connection is
    **dangling** (FR-027).
- **Recreate**: creating a microservice whose normalized name equals a dangling connection's name
  re-attaches that connection, whether the microservice is created on the canvas or by a document.
- **Reassign**: point the dangling end at an existing microservice; its name is copied over.

**Rationale**: The connections document refers to microservices by name, so the name is the
identity users see. The ID keeps scenarios and the layout stable across renames. The
denormalized name makes dangling connections displayable and matchable without tombstone rows.

**Alternatives considered**:

- Connections keyed by name only. It was rejected because scenarios and positions would break on
  rename.
- Tombstoning deleted microservices. It was rejected because the hidden rows complicate the
  uniqueness rules and the "recreate" case.

## R3. Connection identity and uniqueness

**Decision**:

- A connection's identity is `(sourceNameNormalized, targetNameNormalized, nameNormalized)`.
  - It is enforced by a unique constraint.
  - It covers several connections per pair, in either direction (FR-017).
  - It is also the matching key when a document is applied (R5).
- Each connection also has a UUID `id`. It is used by the canvas, the API and scenarios, and it is
  preserved when an applied document matches the connection.
- The `kind` is `REQUEST`, `EVENT` or null. In the document it is written lowercase:
  `request` or `event`.

**Rationale**: Keying on names, not IDs, makes dangling connections match too. When no connections are
dangling, exporting and then re-importing a document is therefore an exact no-op (SC-003).
Dangling connections are not exported (R4), so a re-import lists them under Removed in the preview.

## R4. Connections document format (v1)

**Decision**:

```json
{
  "formatVersion": 1,
  "microservices": [
    { "name": "Orders", "description": "Order lifecycle" },
    { "name": "Billing" }
  ],
  "connections": [
    { "name": "Create invoice", "source": "Orders", "target": "Billing", "kind": "request" },
    { "name": "Order placed", "source": "Orders", "target": "Billing", "kind": "event",
      "description": "Published on checkout" }
  ]
}
```

- `formatVersion` is required and equals `1`. `connections` is required and may be empty.
  `microservices` is optional.
- Each microservice entry has `name` (required, 1–120 characters) and `description`
  (optional, up to 2000 characters, may be null).
- Each connection entry has `name`, `source` and `target` (required, 1–120 characters),
  `kind` (optional: `request` or `event`) and `description` (optional, up to 2000 characters).
- Unknown properties anywhere are errors. Name comparison uses the normalization from R2.
- **Export** is deterministic, so exports diff cleanly in version control:
  - Microservices are sorted by normalized name, and connections by (source, target, name).
  - Optional properties that are absent are omitted.
  - Two-space indentation and a trailing newline.
- The export lists every microservice, including those without connections, and every **attached**
  connection (FR-025). Dangling connections have a missing end, so the document can't express them. They
  are left out, counted in `excludedDanglingConnections`, and the UI warns about them. Re-importing
  such an export lists them under Removed in the preview (R5).
- Limits: the document is at most 2 MB, with at most 5,000 connections and 2,000 microservices.

**Rationale**:

- The format is small and readable by hand, and the version number allows it to evolve.
- Microservices are referenced by name rather than by ID so teams can write the document
  without the app.
- Lowercase kinds read naturally in a hand-written file.

**Alternatives considered**:

- YAML. It was rejected because the spec names JSON.
- Microservice IDs in the document. They were rejected because the document would not be
  writable or reviewable by hand.

## R5. Applying a document: validate, diff, preview, apply

**Decision**:

1. **Validate** the whole document and collect *all* errors. The kinds of error are listed in
   R6. If any error exists, nothing is applied (FR-023).
2. **Diff** the document against the current baseline:
   - **Microservices**:
     - A name that doesn't exist (listed, or used by a connection) → *added*.
     - A listed entry whose `description` property is present and differs → *changed*.
     - If the `description` property is absent, the description is left as it is.
     - Microservices that are not mentioned are never removed (FR-021a).
   - **Connections** are matched by the R3 key:
     - In the document only → *added*.
     - In both, with a different `kind` or `description` → *changed*, and the ID is kept.
     - In the baseline only, including dangling connections → *removed* (replace semantics, FR-021).
3. **Preview** (`POST /api/connections-document/preview`) returns the diff and changes nothing.
4. **Apply** (`PUT /api/connections-document`) re-validates and re-diffs. It runs in one
   transaction, guarded by the workspace `revision` (R7). A stale `baseRevision` returns `409`.
   Microservices added by the document get no spec and no position; the client places them
   (002/R11).

**Rationale**: Re-diffing on apply prevents the preview going stale between preview and
confirm. All-or-nothing satisfies SC-004.

**Note**: A document that matches the baseline exactly yields an empty diff. The UI shows
"No changes", which is the observable proof of the round trip.

## R6. Precise error positions

**Decision**:

- The backend receives the document as **text**, in a JSON string field named `text`, not as
  parsed JSON. Two passes follow:
  1. **Syntax.** A Jackson streaming parse. A syntax error reports its line and column, taken
     from `JsonProcessingException.getLocation()`.
  2. **Semantics.** `JsonLocator` makes one streaming pass and records the start location of
     every value under its JSON Pointer. Each semantic error then carries:
     - `pointer`, for example `/connections/3/target`;
     - `path`, the display form, for example `connections[3].target`;
     - `line` and `column`;
     - `message`.
- Error kinds:
  - `SYNTAX`
  - `UNSUPPORTED_VERSION`
  - `MISSING` (also for blank values)
  - `TOO_LONG`
  - `INVALID_KIND`
  - `SELF_CONNECTION`
  - `DUPLICATE_CONNECTION` (points at the second occurrence and names the first)
  - `DUPLICATE_MICROSERVICE` (in the list)
  - `UNKNOWN_PROPERTY`
  - `WRONG_TYPE`
  - `TOO_MANY`
- The frontend editor shows these errors as inline markers on their lines.

**Rationale**: Users edit the document as text. Positions in that text are the most precise
errors possible, and they map directly onto editor markers.

**Alternatives considered**: JSON Schema validation (networknt). It was rejected because it
reports schema paths without source positions, and cross-entry rules (duplicates, self connections)
need custom code anyway.

## R7. Workspace revision for concurrent edits

**Decision**:

- A single-row `workspace_state(revision)` is incremented by every baseline write that
  affects the connections document: creating, renaming, describing or deleting a microservice, and
  creating, updating, deleting or reassigning a connection, or applying a document.
- `GET /api/canvas` and `GET /api/connections-document` return the current revision.
- The editor sends `baseRevision` on save. The canvas writes don't send it; the server
  serializes them.

**Rationale**: One editor shares their screen, but two browser tabs are still possible (the
002 edge case). This stops a stale editor buffer silently overwriting connections drawn on the
canvas in the meantime. A single counter is enough because the document is the unit of
editing.

## R8. Docked spec cards on the canvas

**Decision**:

- A linked card is a React Connection child node: `parentId = ms:{id}`, with its position relative
  to the parent at `{x: 0, y: parentHeight + 12}`. It therefore moves with its microservice
  (FR-014).
- `extent` is not set, so the card may stick out of the parent's box. Docked cards cannot be
  dragged on their own (`draggable: false`).
- A collapsed card is not rendered as a node. The microservice shows an "API: {title}" badge
  that expands it again. Collapsed card IDs are saved in the layout (`collapsedCards`).
- ELK layout treats a microservice and its docked card as one block, using their combined
  measured size. Unlinked cards are laid out as separate nodes in a final row (002/R11 is
  otherwise unchanged).
- Unlinking places the card at the parent's absolute position plus a 40 px offset, then saves
  the layout.

**Rationale**: Parent and child nodes are React Connection's built-in way of attaching nodes, so
there is no manual position syncing.

**Alternatives considered**: A separate node whose position is synced on drag. It was rejected
because it is fragile with multi-select drags and causes extra layout writes.

## R9. Scenario content v2

**Decision**: Scenario documents use `formatVersion: 2`. The content contains:

| Property | Contents |
|---|---|
| `proposedMicroservices[]` | `{ tempKey: "proposed:…", name, description?, card?: { title, endpoints: ProposedEndpoint[] } }`. A proposed microservice may carry a proposed card |
| `proposedLinks[]` | `{ microservice: msId \| tempKey, cardId }`. The card must be unlinked in the baseline, or its baseline link must be proposed for removal |
| `proposedConnections[]` | `{ id, source, target, name, kind?, description? }`. Source and target are an `msId` or a `tempKey`. The R3 uniqueness applies across baseline and proposed connections |
| `addedEndpoints[]` | `{ cardId, endpoint }` |
| `addedFields[]` | `{ cardId, endpointKey, direction, field }`. Nested paths as in 002 (T108) |
| `removals` | `{ connections: connectionId[], microservices: msId[], links: msId[] }`. Removing a microservice implies removing its connections and its link; the client derives this |
| `connectionChanges[]` | `{ connectionId, name?, kind?: REQUEST \| EVENT \| NONE, description? }` |
| `positions` | Keyed by `ms:{id}`, `card:{id}` or the `tempKey` |

- The orphan check applies to every msId, cardId, endpointKey and connectionId the scenario
  references (FR-042).
- `baselineRef` is `{ cards: {cardId: contentHash}, revision }`, used to detect drift.
- **v1 import**: `LegacyScenarioConverter` (R10) turns a 002 export into v2 and returns notes
  on anything it dropped.
- Exporting the connections document "with the scenario applied" is a client-side transformation
  of the baseline document: add the proposed connections, drop the removals, apply the changes, add
  the proposed microservices (FR-043). The output is a plain v1 document.

**Rationale**: Proposals reference baseline entities by stable ID, so renaming a microservice
doesn't orphan a scenario.

## R10. Migrating 002 data (one-time)

**Decision**:

- A **Flyway Java migration** `V5__Migrate002Data` runs between two SQL migrations:
  - `V4` creates the new tables;
  - `V6` renames the old tables to `legacy_*`.
- The Java migration is registered as a Spring `JavaMigration` bean, which Spring Boot passes
  to Flyway. It uses plain JDBC and a private Jackson mapper, and works on both H2 and
  PostgreSQL. JSON columns are written as `? FORMAT JSON` on H2 and `?::jsonb` on PostgreSQL,
  chosen by `connection.getMetaData().getDatabaseProductName()`.
- **Services → microservices and cards**:
  - `service_spec.id` becomes the card ID. The card title is `display_name`, and the parsed
    endpoints are re-keyed from `serviceKey:` to `cardId:`.
  - Each service creates a microservice named after its `display_name`, linked to the card.
  - If two services had the same display name, the later one is renamed `Name (2)`,
    `Name (3)` and so on. Each rename is noted in the report.
  - Positions keyed by `serviceKey` move to `ms:{id}`.
- **Edges → connections**:
  - Only `edge_override` rows of kind `CONFIRMED` or `MANUAL` with `deleted_at IS NULL` are
    migrated.
  - The connection name is the row's `note`, or `"{source} → {target}"` when there is no note.
  - Endpoint-level rows between the same pair that would collide get the suffix
    ` ({METHOD path} → {METHOD path})`.
  - The kind is null. `DISMISSED` rows and `inference_settings` are only counted.
- **Scenarios** are converted v1 → v2 with `LegacyScenarioConverter`:
  - Proposed services become proposed microservices, each with a proposed card.
  - Proposed edges become proposed connections. The name is the edge label, or `"A → B"` when it has
    none. Endpoint handles are dropped and noted in the report.
  - Elements that reference service keys that no longer exist are kept as orphans and noted.
- **Report**: the counts and notes are written to `migration_report` (a single row, with the
  time it ran). `GET /api/migration-report` serves it, and the UI shows it once. Flyway's
  history table guarantees the migration runs only once (FR-046).
- `legacy_*` tables are kept, untouched, for one release so the migration can be checked. A
  later feature drops them.

**Rationale**: Data migrations must run once, atomically, and before the application uses the
new tables. Flyway already orders and records them. A Java migration can reuse the tested v1
→ v2 scenario converter, which SQL can't.

**Alternatives considered**:

- A startup `ApplicationRunner`. It was rejected because it races with the first requests
  and needs its own "ran once" bookkeeping.
- A pure SQL migration. It was rejected because rewriting JSON scenario content in SQL is not
  portable between H2 and PostgreSQL.

**Safety**: [quickstart.md](./quickstart.md) asks for a copy of `backend/data/` before the
first start. The migration itself never deletes the 002 rows; it renames their tables.

## R11. Connection rendering: kinds, parallel connections and bundling

**Decision**: A custom `ConnectionEdge`:

- **Style by kind** (FR-018b), as inline styles so they survive PNG export (002/R13):

  | Kind | Line |
  |---|---|
  | `request` | Solid, 2 px |
  | `event` | Dashed `6 4` |
  | None | Dotted `2 4`, neutral grey |

- **Draft Scenario Mode**: proposed connections keep their kind's line style and use the green
  proposed color (002/R13).
  - A proposed removal is drawn faded red with a strike-through on its label.
  - A proposed change shows its label as `old → new`.
- **Parallel connections**: the client flows connections by the unordered microservice pair. For a flow
  of n connections, the i-th connection gets a perpendicular offset of `(i - (n-1)/2) * 18 px` along a
  quadratic curve. Opposite-direction connections share the flow, so they never overlap.
- **Labels** show the connection name and are hidden below the `overview` zoom tier (002/R12).
- **Bundling**: below the `overview` tier, a flow with more than one connection is drawn as a single
  edge labelled `×n`. Clicking it zooms in.
- A legend explains the three line styles and the proposed styles.

**Rationale**: Offsets keep every connection clickable, which addresses the known 002 issue that
overlapping edges were hard to click. Grouping is presentation logic over backend data, which
Principle V allows.

## R12. Connections document editor

**Decision**:

- **CodeMirror 6**, through `@uiw/react-codemirror` with `@codemirror/lang-json` and
  `@codemirror/lint`, gives syntax highlighting, local well-formedness checks and inline
  markers for backend errors.
- The editor opens the exported document text and keeps its buffer in memory only, which the
  constitution treats as transient UI state.
- **Save** calls *preview*, shows the change dialog, then calls *apply* with `baseRevision`.
- Closing the editor with unsaved changes asks whether to save or discard.
- The editor's **Upload** button and the toolbar's **Import connections** both load a file into the
  same preview connection.

**Rationale**: About 150 KB gzipped for a real editor experience. A plain textarea can't show
markers on lines.

**Alternatives considered**: Monaco. It was rejected because it is around 2 MB and its web
worker setup is complicated in Vite.

## R13. Search and highlighting

**Decision**:

- Search stays client-side over the backend-provided fields (002/R14).
- It also matches endpoint paths and `operationId`, case-insensitively and partially.
- Each result carries its card and that card's linked microservice name, or "unlinked".
- Highlighting a match highlights the card and its microservice.
- Blast radius: clicking a microservice highlights its directly connected microservices
  through baseline connections (and proposed connections in draft mode), the connections between them, and
  their docked cards.

**Rationale**: It is unchanged in spirit. It is filtering of data the backend already
computed, not domain logic.

## R14. Testing strategy (changes to 002/R16)

**Decision**:

- **Removed**: `EdgeInferenceEngineTest`, `ReferencePrecisionTest`, `GraphServiceTest`,
  `EdgeOverrideIT`, `InferenceSettingsIT`.
- **New unit tests**:
  - `ConnectionsDocumentValidatorTest`: every error kind, with exact positions;
  - `ConnectionsDocumentDiffTest`: add, change and remove, dangling matching, description absent
    vs. null;
  - `ConnectionsDocumentWriterTest`: deterministic order and round trip;
  - `JsonLocatorTest`;
  - `LegacyScenarioConverterTest`;
  - `CanvasServiceTest`: child nodes, collapsed cards, dangling connections.
- **New integration tests** (Testcontainers PostgreSQL):
  - `SpecCardIT`: import, duplicate, sync, delete, and no connection changes (FR-007);
  - `MicroserviceLinkIT`: 1:1 rules, replace, rename cascade, delete makes connections dangling,
    recreate re-attaches them;
  - `ConnectionIT`: CRUD, uniqueness per pair, self connection rejected, reassign;
  - `ConnectionsDocumentIT`: preview and apply, stale revision, all-or-nothing, export → import gives
    an empty diff (SC-003), 200 connections with 100 microservices in under 5 s (SC-005);
  - `ScenarioIT`: v2 CRUD, orphans, v1 import;
  - `Migration002IT`: runs Flyway to V3, seeds 002 data, migrates to the latest version, and
    asserts the counts, names, positions, scenarios and report (SC-008). It also runs on H2.
  - `ContractConformanceIT`: kept.
- **Frontend**: Vitest for:
  - `parallelOffsets`, bundling and `connectionStyle`;
  - the overlay with removals and changes, and `applyScenarioToDocument`;
  - the editor's save connection (with a mocked API);
  - docked card positioning.
- **Playwright**: import a card → create a microservice → link it → upload a document →
  draw a connection → export → re-import (no changes) → draft a removal.

## R15. Versions

Unchanged from 002/R17. New frontend dependencies, pinned:

- `@uiw/react-codemirror` ^4.23
- `@codemirror/lang-json` ^6.0
- `@codemirror/lint` ^6.8

There are no new backend dependencies: Jackson's streaming API is already on the classpath.

---

## Implementation verification (2026-11-22)

All merge gates from [quickstart.md](./quickstart.md#automated-checks-merge-gates) pass:

| Gate | Result |
|---|---|
| `./mvnw verify` | 38 unit tests and 33 integration tests pass (PostgreSQL 16 via Testcontainers, plus H2 for the migration), Spotless clean |
| `npm run typecheck` / `npm run lint` | no errors, no warnings |
| `npm test` (Vitest) | 55 tests in 11 files |
| `npm run e2e` (Playwright) | `declared-connections.spec.ts` passes; three consecutive runs were stable |

`ConnectionsDocumentIT` covers SC-003 (export → re-import is a no-op) and SC-005 (200 connections / 100 microservices in
under 5 s). `Migration002IT` covers SC-008 on both databases. `PerformanceSmokeIT` covers SC-001 (200-endpoint
import) and the canvas build at 100 microservices, 100 cards and 500 connections.

Quickstart V1–V5 were exercised through the Playwright run and a visual check of the rendered canvas
(screenshots of a docked card, parallel labelled connections and the three line styles). V6 (migration on the
real 002 database) is T082 and waits for the user's go-ahead.

### Deviations from the plan, and why

- **Build order.** Removing the `graph`, `inference` and `edge` packages broke the `service`, `layout` and
  `scenario` packages at once, so the backend was rebuilt in dependency order (entities → services →
  canvas → scenario v2 → migration) instead of strictly story by story. Every task was still completed
  and checked off.
- **PATCH bodies are read as a JSON tree** (`common/PatchBody`), so "property absent" (unchanged) and
  `null` (clear) can be told apart without adding `jackson-databind-nullable`. The contract schemas
  `MicroservicePatch` and `ConnectionPatch` are unchanged.
- **Re-syncing a pasted card** accepts an optional `{ content }` body, as 002 did. The contract's
  `syncCard` operation now documents it.
- **Docked card offset.** The backend's fixed `{0, 84}` offset is only a starting value. The canvas places
  a docked card just below its microservice's *measured* height, because descriptions and the spec toggle
  make microservice nodes taller than 84 px (found in the screenshot check).
- **Connection labels are clickable and drawn above nodes**, so a connection stays selectable where its line passes
  under another node (found by the end-to-end test).
- **Entering Draft Scenario Mode flushes a pending baseline layout save**, so nothing baseline-related is
  written while drafting (Principle IV; found by the end-to-end test).

## R16. Flows (colors), added after the first implementation

**Decision**: named flows with a color from a fixed palette of eight (blue, purple, orange, pink, amber,
brown, cyan, slate), stored in `flow` and referenced by a nullable `connection.flow_id`. A grouped connection's
stroke takes the flow color; the line style still encodes the kind. Proposed connections stay green and removals
red in draft mode, so the palette leaves those out (Principle IV). The legend lists flows; clicking one
highlights its connections. The connections document gains `flows[]` and `connections[].flow` (by name), validated like the
rest of the document.

**Rationale**: the user asked for colors "so it's easy to follow connections". A named flow recolors a whole
business process at once and labels it in the legend and in exports; a fixed palette keeps colors
distinguishable on screen share and in PNG exports.

**Alternatives considered**: a free color per connection, rejected by the user in favour of flows; free hex
colors, rejected because they collide with the proposed and removed colors and with each other.

### Follow-up verification: menu fix and flows (2026-11-22)

- **Node menus** are now rendered in a floating layer anchored to their ⋯ button
  (`ui/FloatingMenu.tsx`), because a docked card drawn on top of its microservice covered the menu, and
  its Close button could not be clicked. The end-to-end test opens such a menu and clicks Close.
- **Flows** (FR-049 – FR-053, R16) pass every gate: `./mvnw verify` (41 unit, 37 integration
  tests), typecheck, lint, Vitest (60 tests) and Playwright, which creates a flow, assigns a connection,
  highlights it from the legend and checks that the export round trip, flows included, shows no changes.

## R17. Connection endpoint, added after the first implementation

**Decision**: a connection may name one endpoint of its **target** microservice, stored as text `METHOD /path`
in `connection.endpoint`. The canvas computes `endpointMissing` against the target's linked card (method plus
normalized path), so a re-sync or a different link flags the connection instead of changing it. The editor
offers the target card's endpoints, plus the current value when it is no longer in the spec.

**Rationale**: the user chose the target's endpoint over "either side" and "several endpoints". Text
storage keeps connections independent of spec cards (Principle II: connections survive re-import and unlinking) and
lets hand-written documents name endpoints of microservices that have no spec yet.

**Alternatives considered**: a foreign key to the endpoint (rejected: endpoints are embedded JSON and change
on every re-sync); endpoints of either side, or several per connection (rejected by the user).
- **Connection endpoint** (FR-054 – FR-056, R17) passes every gate: `./mvnw verify` (43 unit, 38 integration
  tests), typecheck, lint, Vitest (63 tests) and Playwright, which picks the target's `POST /orders` for a
  drawn connection and keeps the export round trip at "No changes".

## R18. Terminology: connections and flows

**Decision**: the user clarified that a *flow* is a business process across several microservice links, and a
*connection* is one link. The implementation was renamed end to end: tables (`V9`), saved scenario JSON keys
(`V10`), REST paths, DTOs, the documents and the UI. The connections document is `formatVersion` 2 and the
reader still accepts version 1 by switching its property names (`flows` → connections, `groups` → flows,
`group` → flow), so error positions stay exact. Scenario documents are `formatVersion` 3; version 2 is
converted by the same key renames as `V10`.

**Endpoint badge**: the endpoint was a small second line of the name label and disappeared when zoomed out.
It is now its own badge on the curve near the arrowhead (78% along it), with the method badge and the path.
