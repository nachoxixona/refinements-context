# Research: Microservice Architecture & Scenario Visualizer

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-21

Each entry records the decision, why it was chosen, and the alternatives that were rejected.
Items marked **Verify during implementation** depend on library details that the first task
touching that area must confirm against the version actually pinned.

---

## R1. Spec parsing and `$ref` resolution

**Decision**: Use the umbrella artifact `io.swagger.parser.v3:swagger-parser` (2.1.x), through
`OpenAPIParser().readContents(content, location, options)`, where `location` is the source URL for
URL specs (so relative refs resolve) and `null` for pasted specs, with:

- `setResolve(true)` and `setResolveFully(true)` so every `$ref` is inlined before extraction.
- `setResolveCombinators(false)` so `allOf`/`oneOf`/`anyOf` stay visible and the field
  flattener (R3) can merge `allOf` and label `oneOf`/`anyOf` branches itself.
- **Relative refs** (`./schemas/order.yaml`, `common.json#/Order`) in a URL spec are resolved
  against that spec's URL. Same-host references are allowed by default and fetched through
  `SpecFetcher`, with the same scheme, size and timeout limits (Principle I: file-relative
  refs MUST be resolved).
- **References to other hosts** are blocked unless they are on
  `visualizer.refs.remote-allowlist` (empty by default), using the parser's safe-URL options
  (`setSafelyResolveURL(true)` with an allowlist). Blocked refs are reported as unresolved
  (constitution: Architecture & Data Constraints).
- **Pasted specs** have no base location, so relative refs in them are reported as unresolved,
  with a hint to register the spec by URL instead.

The umbrella artifact includes the Swagger 2.0 → OpenAPI 3.0 converter, so `OpenAPIParser`
returns an `OpenAPI` model for both versions and the rest of the pipeline sees one canonical
model (Principle I).

The backend fetches the top-level document itself (R2) and passes the **contents plus the source
URL** as the base location, so relative refs resolve while timeouts, size limits and allowed
schemes stay under our control.

**Verified during implementation (swagger-parser 2.1.49)**: `OpenAPIV3Parser` has a
`readContents(..., location)` overload, but `OpenAPIParser` (the entry point that also runs the
Swagger 2.0 converter) does not. `SpecParser` therefore uses the fallback: it walks the document,
fetches every external `$ref` through `SpecFetcher` (same-host or allowlisted only), stages the
documents as local JSON files with their refs rewritten, and calls `readLocation` on the staged
root, so the parser itself never opens a network connection. Missing pointers are reported with
their location before parsing. Recursive schemas keep a `$ref` to their component after full
resolution; those are accepted and followed by `FieldFlattener` with its cycle guard. Parsing
OpenAPI 3.1 needs `javax.xml.bind:jaxb-api` at runtime (swagger-core's Jackson introspector).

`SwaggerParseResult.getMessages()` is mapped to user-facing errors. A result with a `null`
model, or with any message about an unresolved reference, is rejected as a whole (FR-005).
Messages already include JSON-pointer-like locations (for example
`attribute paths.'/orders'.get.responses is missing`), which are passed through as
`location`.

**Rationale**: This is the reference Java implementation, already mandated by the constitution,
and it handles both versions and full resolution deterministically.

**Alternatives considered**: `openapi4j` (unmaintained); a hand-written resolver (error-prone,
and it violates Principle I's intent); `readLocation` for URLs (gives up control over fetch
limits and SSRF policy).

**Verified during implementation**: `ParseOptions` has `setSafelyResolveURL` and
`setRemoteRefAllowList`, but they are not needed because the parser only reads staged local files.

## R2. Fetching specs from URLs

**Decision**: Use Spring's `RestClient` on the JDK `HttpClient`, with:

- `http`/`https` schemes only, a 5 s connect and 15 s read timeout, at most 3 redirects;
- a streaming read capped at 10 MB, aborting with `413`-style problem details beyond that;
- no credentials sent (spec assumption: v1 URLs are public within the network).

Private-network addresses are **allowed**, because the services being visualized live on the
internal network. The SSRF exposure is therefore limited to the operator's own network, which
is acceptable for a single-user internal tool. The policy is configurable
(`visualizer.fetch.allowed-hosts`) for later hardening.

**Rationale**: It meets FR-002, FR-007 and FR-008 and the constitution's security baseline
without blocking the core use case.

**Alternatives considered**: Blocking RFC 1918 ranges (would break the main use case);
fetching from the browser (violates "zero parsing in the browser" and CORS would block most
internal specs).

## R3. Field extraction (flattening schemas)

**Decision**: Use a `FieldFlattener` that walks each operation and produces flat `FieldDto`
lists:

- **Inputs**: path, query and header parameters (cookie parameters are ignored) plus request
  body properties. The preferred media type is `application/json`, then the first JSON-like
  one, then the first one.
- **Outputs**: properties of the lowest `2xx` response, using the same media-type preference.
  Arrays contribute their item properties, with the path suffix `[]`
  (`items[].productId`).
- Each field carries `path` (`order.customerId`), `name` (leaf, `customerId`), `type`
  (`string`, `integer`, `object`, `array`…), `format`, `required` and `location`
  (`path|query|header|body|response`).
- `allOf` members are merged. `oneOf`/`anyOf` branches are all included and tagged
  `variant: n`.
- Cycle protection: track visited schema identity on the current path, and stop at depth 6.
  The truncated field is marked `truncated: true`.

**Rationale**: Inference (R4), search (FR-027/028) and nodes (FR-011) all need the same flat,
path-qualified view. Computing it once on the backend satisfies Principle I and Principle V's
"no parsing in the browser".

**Alternatives considered**: Sending raw resolved schemas to the client (puts schema logic in
the browser); following non-2xx responses (error payloads produce noise edges).

## R4. Edge inference and confidence scoring

**Decision**: An `EdgeInferenceEngine` builds an index from **normalized leaf name** to input
fields across all services, then, for every output field of every endpoint, looks up candidate
inputs in **other** services.

- **Normalization**: lower-case and strip `_`, `-` and spaces (`customer_id`, `customerId` and
  `CustomerID` all become `customerid`).
- **Exclusion**: drop any field whose normalized leaf name is on the generic list (R5) **before**
  indexing.
- **Scoring** (0–1, summed and capped at 1.0):

  | Signal | Points |
  |--------|--------|
  | Exact leaf-name match (case-sensitive) | 0.6 |
  | Normalized-only leaf-name match | 0.4 |
  | Compatible type (same type, or integer↔number, or both strings with the same `format`) | +0.2 |
  | Incompatible type | −0.3 |
  | Leaf name is entity-qualified (`<entity>Id`, `<entity>Code`, `<entity>Number`…) | +0.1 |
  | Input is a path parameter or required body field | +0.1 |

  Matches below a floor of **0.4** are not emitted.
- **Aggregation**: one edge per (source endpoint, target endpoint) pair. Its confidence is the
  maximum over its matches, and it lists all matched field pairs.
- **Fan-out cap**: for each output field, keep the 5 best-scoring targets. The edge data gets
  `hiddenCandidates: n` when more were dropped (edge case "matches many endpoints").
- **Determinism**: inputs and outputs are sorted, so the same specs always yield the same edges
  in the same order (Principle I, reproducible tests).

**Rationale**: The scoring is simple, explainable (FR-017), cheap, and testable with fixtures.
At the target scale (50 services, about 100 endpoints each) the index-based join is roughly
O(total fields), well under SC-005's 1 s.

**Alternatives considered**: Embedding or LLM similarity (rejected by Principle I); path-based
matching only (misses body fields); Levenshtein fuzzy matching (too many false positives, which
works against Principle III).

## R5. Generic field list

**Decision**: The defaults come from `application.yml`
(`visualizer.inference.generic-fields: [id, status, timestamp, message, createdAt, updatedAt,
error, code]`) and seed a single `inference_settings` row on first start. The UI edits that
row (`GET/PUT /api/settings/inference`). Matching is on the normalized leaf name.

**Rationale**: "Configurable without a code change" (Principle III) is met both by operators
(config) and by the single editor (UI), as the spec allows anyone to edit it in v1.

**Alternatives considered**: Config file only (the spec requires in-app editing); a full
pattern language (YAGNI).

## R6. Stable identities and why overrides survive re-sync

**Decision**: Every persisted reference uses **natural keys** derived from spec content, never
database IDs.

- `serviceKey`: a slug of `info.title`, plus `@host` of the first server URL when present
  (`order-service`, `order-service@orders.internal`). The user can override it when
  registering a separate service with a duplicate title (edge case), which becomes
  `order-service-2`.
- `endpointKey`: `{serviceKey}:{METHOD} {path}` with path templates normalized
  (`/orders/{orderId}` becomes `/orders/{}`), so renaming a path parameter does not orphan
  edges.
- `edgeKey`: `{sourceEndpointKey}->{targetEndpointKey}` for endpoint edges, or
  `{sourceServiceKey}->{targetServiceKey}` for service-level manual edges.

`EdgeOverride` rows and scenario references store these keys. Each time the graph is built,
an override whose endpoint key no longer exists in the current specs is returned with
`orphaned: true` (FR-023, Principle II). Scenario references are checked the same way when
the scenario is loaded (FR-038).

**Rationale**: Re-sync replaces parsed endpoints wholesale. Natural keys make "verified edges
and dismissals survive re-upload" (SC-004) true by construction.

**Alternatives considered**: Surrogate IDs with reconciliation on re-sync (complex and
fragile); content hashes (change on any edit and lose overrides).

## R7. Inferred edges: computed, not stored

**Decision**: Inferred edges are **not persisted**. `GraphService` computes them from the
current `ServiceSpec` rows and settings, then merges overrides:

1. Start from inferred edges.
2. Remove edges whose `edgeKey` has a `DISMISSED` override (soft-deleted `MANUAL` rows are ignored).
3. Mark edges with a `CONFIRMED` override as `origin: verified` (solid).
4. Add `MANUAL` overrides as verified edges.
5. Flag orphans (R6).

The result is cached in memory under a "graph generation" counter, which goes up on any write
to services, overrides or settings.

**Rationale**: This matches the user's chosen entities (`ServiceSpec`, `EdgeOverride`,
`SavedScenario`) and the `GET /api/graph` "merged inferred + override edges" contract. It
avoids stale inferred rows after rule or denylist changes, and keeps one source of truth.

**Alternatives considered**: Persisting inferred edges (needs invalidation and migration when
rules change).

## R8. Persistence: PostgreSQL in production, H2 locally, Flyway

**Decision**:

- Use Spring Data JPA (Hibernate 6) with `@JdbcTypeCode(SqlTypes.JSON)` for document columns
  (parsed endpoints, scenario content). These map to `jsonb` on PostgreSQL and `JSON` on H2.
- Use Flyway with vendor folders `db/migration/{vendor}` (`postgresql`, `h2`), because DDL for
  JSON columns differs.
- Profiles: `dev` (H2 file database in PostgreSQL compatibility mode), `prod` (PostgreSQL 16).
- Repository and migration tests run against PostgreSQL with **Testcontainers**, so the
  production dialect is always exercised. Fast unit tests need no database.
- Optimistic locking (`@Version`) on `SavedScenario` and on the baseline layout record. A stale
  write returns `409 Conflict`, so the second browser tab is warned (edge case, FR-034).

**Rationale**: This satisfies constitution Principle V (H2 dev, PostgreSQL prod), the
versioned-migrations constraint, and the user's choice of PostgreSQL.

**Alternatives considered**: PostgreSQL only, through Docker, for dev (the constitution names
H2 for local dev); a JSON text column with manual mapping (more boilerplate, no `jsonb`
querying later).

## R9. REST API style and DTO shape

**Decision**: JSON REST under `/api`, documented with **springdoc-openapi**. The contract is
in [contracts/openapi.yaml](./contracts/openapi.yaml). Errors use RFC 9457 Problem Details
(Spring `ProblemDetail`) with an extra `errors[]` of `{location, message}` for parse failures.

`GET /api/graph` returns React Flow–native shapes (Principle V):

- **Node**: `{ id: serviceKey, type: "service", position: {x, y} | null,
  data: ServiceNodeData }`. `position` is `null` for services never positioned, and the client
  places those (R11).
- **Edge**: `{ id: edgeKey, source, target, sourceHandle, targetHandle, type, data: EdgeData }`,
  where `type` is `inferred` or `verified` and `data` carries `origin`, `confidence`,
  `matches[]`, `orphaned` and `hiddenCandidates`.
- **Handles**: `out:{endpointKey}` and `in:{endpointKey}` per endpoint, plus `out:service` and
  `in:service` for service-level edges.

Frontend TypeScript types are generated from `contracts/openapi.yaml` with
**openapi-typescript**, so the DTOs and the client cannot drift apart.

**Rationale**: The client renders the response directly (Principle V). Generated types turn
contract drift into a compile error under strict mode.

**Alternatives considered**: GraphQL (unnecessary for one screen); hand-written TS types
(drift risk).

## R10. Frontend state

**Decision**:

- **Server state** (graph, scenarios list, settings): **TanStack Query**. After a mutation,
  `/api/graph` is invalidated and refetched.
- **Client state**: **Zustand** stores for
  - `canvasStore`: selection, highlight mode, search query, expanded endpoints, filters
    (all/verified/inferred, display-only `displayMinConfidence`);
  - `scenarioStore`: `isDraftMode`, the working draft (proposed services, endpoints and edges
    keyed by temporary IDs), layout overrides, a `dirty` flag, and the base `version` for
    conflict detection.

  Draft edits live only in `scenarioStore` until **Save** calls `POST/PUT /api/scenarios`
  (as the user requested). The baseline graph from the server is **never mutated** in draft
  mode. The canvas renders `baseline ⊕ draft overlay` as a derived, memoized selector, so
  toggling draft mode off just stops rendering the overlay (FR-033, SC-007).
- A `beforeunload` guard, plus a confirmation when leaving draft mode, protects a dirty draft.

**Rationale**: Unsaved draft edits are transient UI state, like unsaved drags, which the
constitution's "no local persistence of domain state" rule explicitly allows. Saved scenarios
are stored on the backend.

**Alternatives considered**: Redux Toolkit (more boilerplate for the same result); persisting
every draft change to the server as it happens (chatty, and not what the user asked for).

## R11. Layout with elkjs while keeping dragged positions

**Decision**:

- Use `elkjs` (bundled build, run in a Web Worker), `algorithm: layered`,
  `elk.direction: RIGHT`, with node sizes measured from React Flow
  (`node.measured.width/height`).
- **Auto-arrange** (explicit button): run ELK on the whole visible graph and apply all
  positions. Positions are saved only when the user confirms (FR-014).
- **New services without a position**: if *no* node has a position (first load), run ELK once
  and save. Otherwise place new nodes in a column to the right of the current bounding box,
  without moving existing nodes.
- Drags update local state immediately. Positions are saved in a debounced (1 s) batch with
  `PUT /api/layout` (with `version`). In draft mode, positions go into the scenario's layout,
  not the baseline.

**Rationale**: Principle III requires both automatic layout and keeping user positions, with
auto-layout never silently overwriting them.

**Alternatives considered**: dagre (weaker for port-based layouts); ELK `interactive` mode
(harder to reason about, and it can still move nodes).

## R12. `CustomServiceNode`: zoom levels and handles

**Decision**:

- Zoom tiers read from `useStore(s => s.transform[2])` through a selector that returns only the
  tier, so a node re-renders only when its tier changes:
  - **< 0.45**: name + endpoint count + service-level handles only;
  - **0.45–0.9**: endpoints as rows with method badges;
  - **≥ 0.9**: expandable request/response fields.
- **Endpoint rows**: a colored method badge (GET green, POST blue, PUT orange, PATCH teal,
  DELETE red, each with its text label, so color is never the only cue), the path, and an
  expand chevron.
- **Handles**: each endpoint row renders `in:`/`out:` handles. When the node or endpoint list
  is collapsed (low zoom or the user collapsed it), edges are re-targeted to the service-level
  handles in a derived selector, so every edge always has a rendered handle. React Flow needs
  `updateNodeInternals(nodeId)` after expand or collapse.
- Large services (> 25 endpoints) show a scrollable list with a "Show all" toggle.
- `React.memo` on the node and stable `data` references from the selector keep 50 nodes and
  500 edges smooth (SC-006).

**Rationale**: This implements FR-010–FR-012 and Principle V's zoom-level requirement, and
avoids React Flow edges pointing at handles that are not rendered.

## R13. Visual encoding (UI contract)

| Element | Style |
|---------|-------|
| Inferred edge | Dashed grey, arrow marker. Label with confidence on hover/selection. |
| Verified edge (confirmed or manual) | Solid dark, arrow marker. |
| Orphaned verified edge | Solid amber with a ⚠ marker and a "needs review" tooltip. |
| Proposed service (scenario) | Dashed green border, "PROPOSED" tag. |
| Proposed endpoint on a baseline service | Green row background with a "+" marker. |
| Proposed edge | **Green dashed**, animated off by default. |
| Blast-radius upstream | Blue outline. Edges to the selection are highlighted. |
| Blast-radius downstream | Purple outline. |
| Dimmed | 25% opacity. |
| Search match | Yellow outline. Matching field rows marked with an IN/OUT tag. |
| Draft mode active | Green banner "Draft scenario: {name}", plus a green canvas frame. |

A **presentation** toggle increases font size and stroke width, and a full-screen button uses
the Fullscreen API (FR-040, screen-sharing readability).

## R14. Blast radius and field search: client-side over backend data

**Decision**: Both run in the browser over the `GraphResponse` already loaded (plus the draft
overlay):

- **Blast radius** (FR-024–FR-026): one hop. Upstream is the edge sources whose target is the
  selected node; downstream is the edge targets whose source is the selected node. Results are
  de-duplicated with a `Set`, so bidirectional pairs and cycles are safe. A summary chip shows
  the upstream and downstream counts. Highlighting respects the active edge filter (all,
  verified only, or inferred only).
- **Field search** (FR-027/028): case-insensitive and normalized (same rule as R4) against
  `field.name` and `field.path`, which the backend has already flattened.

**Rationale**: This is filtering and highlighting of backend-provided data. It involves no
schema parsing, `$ref` resolution or edge inference, so Principle V holds. Doing it locally
easily meets SC-005 (< 1 s) and SC-009.

**Alternatives considered**: A server-side `/api/search` (adds latency for no gain at this
scale; can be added later if workspaces grow).

## R15. Export and import

**Decision**:

- **JSON export**: the client serializes the scenario as `ScenarioDocument` (schema in
  [contracts/openapi.yaml](./contracts/openapi.yaml)), with `formatVersion: 1`, the scenario
  content, and the `baselineRef` (service keys and spec versions it was drawn against).
  Unsaved drafts can be exported too.
- **JSON import**: `POST /api/scenarios/import` validates the document on the backend, stores
  it as a new `SavedScenario`, and returns orphan flags (FR-038).
- **Image export**: `html-to-image` `toPng` of the React Flow viewport, framed with
  `getNodesBounds` and `getViewportForBounds` (the approach the React Flow docs recommend),
  at 2× pixel ratio. The legend and the draft banner are included.

**Rationale**: This covers FR-035/036. Running validation on the backend for import keeps
domain rules in one place.

**Alternatives considered**: Server-side rendering of images (heavy, and duplicates the
canvas).

## R16. Testing strategy

| Layer | Tooling | Focus |
|-------|---------|-------|
| Backend unit | JUnit 5, AssertJ | `FieldFlattener`, `EdgeInferenceEngine` (scores, denylist, fan-out cap, determinism), key derivation |
| Backend parsing | JUnit 5 + fixture specs in `src/test/resources/specs/` | Swagger 2.0 YAML/JSON, OAS 3.0/3.1 YAML/JSON, `$ref` chains, `allOf`, cycles, unresolved and remote refs, invalid input |
| Backend integration | `@SpringBootTest` + MockMvc + Testcontainers PostgreSQL | Endpoint contracts, re-sync keeps overrides, orphan flagging, 409 on stale version, Flyway migrations |
| Frontend unit | Vitest + React Testing Library | Blast radius, search, the draft overlay selector (baseline untouched), handle re-targeting, scenario serialization |
| Type safety | `tsc --noEmit` with `strict: true` | Generated API types |
| End-to-end smoke | Playwright (one spec) | The [quickstart.md](./quickstart.md) happy path |

The fixture set includes a **reference workspace** (6 services, known true edges) used to track
SC-003 precision (≥ 80%) in CI as a test assertion.

## R17. Versions pinned for the plan

| Component | Version |
|-----------|---------|
| Java | 21 (LTS) |
| Spring Boot | 3.5.x (latest 3.x patch at implementation time) |
| swagger-parser | `io.swagger.parser.v3:swagger-parser` 2.1.x (latest) |
| springdoc-openapi | 2.8.x (matching Boot 3.5) |
| PostgreSQL | 16 |
| Flyway | managed by the Boot BOM |
| Testcontainers | managed by the Boot BOM |
| Node.js | 22 LTS |
| React | 18.3 (as requested) |
| TypeScript | 5.x, `strict: true` |
| Vite | latest version compatible with React 18 plugin |
| @xyflow/react | 12.x |
| elkjs | 0.10.x |
| TanStack Query | 5.x |
| Zustand | 5.x |
| html-to-image | 1.11.x (the version React Flow docs pin, since later versions had export regressions) |
| openapi-typescript | 7.x |

**Pinned at setup (2026-11-21)**: Spring Boot 3.5.16, swagger-parser 2.1.49, springdoc 2.8.17,
@xyflow/react 12.12, elkjs 0.10.2, Vite 7.3, html-to-image ~1.11.13 (kept on 1.11 as planned; the
React Flow docs claim was not re-checked online). Implementation note: html-to-image did not carry
stylesheet strokes on SVG edge paths into the PNG, so edge and legend strokes are inline styles
(`canvas/edges/edgeStyle.ts`).
