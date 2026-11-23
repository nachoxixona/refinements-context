# Data Model: Microservice Architecture & Scenario Visualizer

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

There are three core persisted entities, as requested: `ServiceSpec`, `EdgeOverride` and
`SavedScenario`. Two small supporting records are needed by the spec: `BaselineLayout`
(FR-014) and `InferenceSettings` (FR-016). Inferred edges are **computed**, not stored
([R7](./research.md#r7-inferred-edges-computed-not-stored)).

All cross-entity references use **natural keys** (`serviceKey`, `endpointKey`, `edgeKey`), not
database IDs ([R6](./research.md#r6-stable-identities-and-why-overrides-survive-re-sync)).

```text
ServiceSpec 1 ──< ParsedEndpoint (embedded JSON) 1 ──< Field (embedded JSON)
EdgeOverride  ── references ──> endpointKey / serviceKey (natural keys, may be orphaned)
SavedScenario ── references ──> serviceKey / endpointKey of the baseline (may be orphaned)
BaselineLayout (single row) ── positions keyed by serviceKey
InferenceSettings (single row)
```

---

## 1. ServiceSpec (table `service_spec`)

A registered microservice and its latest successfully parsed spec.

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID | PK, surrogate, used internally only |
| `serviceKey` | string(200) | **Unique**, required. Slug from `info.title` (+ `@host`); see R6 |
| `displayName` | string(200) | Required. From `info.title` |
| `apiVersion` | string(50) | From `info.version`, may be null |
| `specFormat` | enum | `SWAGGER_2_0`, `OPENAPI_3_0`, `OPENAPI_3_1` |
| `sourceType` | enum | `PASTE`, `URL` |
| `sourceUrl` | string(2048) | Required if `sourceType = URL`. Scheme must be `http` or `https` |
| `rawContent` | text | Required. The last **successfully** parsed raw spec, max 10 MB (FR-008) |
| `contentHash` | string(64) | SHA-256 of normalized `rawContent`. Makes re-sync of identical content a no-op (FR-006) |
| `endpoints` | JSON | `ParsedEndpoint[]` (below), sorted by `path`, then `method` |
| `lastSyncAt` | timestamp | Time of the last sync **attempt** |
| `lastSyncStatus` | enum | `OK`, `FAILED` |
| `lastSyncErrors` | JSON | `ParseError[]` from the last failed attempt, empty when OK (FR-007) |
| `createdAt`, `updatedAt` | timestamp | Audit (FR-041) |

### Embedded: ParsedEndpoint

| Field | Type | Rules |
|-------|------|-------|
| `endpointKey` | string | `{serviceKey}:{METHOD} {normalizedPath}`, unique within the service |
| `method` | enum | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`, `TRACE` |
| `path` | string | Original path template (`/orders/{orderId}`) |
| `operationId`, `summary` | string | Optional, for display |
| `inputs` | `Field[]` | Path, query and header parameters plus request body properties |
| `outputs` | `Field[]` | Properties of the lowest 2xx response |

### Embedded: Field

| Field | Type | Rules |
|-------|------|-------|
| `path` | string | Dot path with `[]` for arrays (`items[].productId`) |
| `name` | string | Leaf name (`productId`) |
| `normalizedName` | string | Lower-case, `_`/`-`/space stripped. Used by inference and search |
| `type` | string | JSON Schema type or `unknown` |
| `format` | string | Optional (`uuid`, `date-time`…) |
| `location` | enum | `PATH`, `QUERY`, `HEADER`, `BODY`, `RESPONSE` |
| `required` | boolean | |
| `variant` | integer | Optional `oneOf`/`anyOf` branch index |
| `truncated` | boolean | True when the depth or cycle limit stopped expansion |

### Lifecycle

```text
          register (valid)               re-sync (valid, new hash)
 (none) ────────────────────▶ OK ◀───────────────────────────┐
                               │                             │
                               │ re-sync (invalid/unreachable)│
                               ▼                             │
                            FAILED ──────────────────────────┘
          (rawContent and endpoints keep the last good version; errors recorded)
```

- Registering invalid content creates **no** row (FR-005).
- If the `serviceKey` already exists and the request does not set `mode=UPDATE` or
  `mode=NEW`, the API returns `409` with `conflict: DUPLICATE_SERVICE` (edge case).
  `mode=NEW` derives a suffixed key (`-2`, `-3`, …).
- Deleting a service (FR-009) removes the row. Overrides that reference it become orphaned;
  they are kept, not cascaded (Principle II).

### Traceability

Each graph node carries its spec's `specFormat`, `sourceUrl`, `contentHash` and `lastSyncAt`
(contract `ServiceNodeData`). `contentHash` identifies the exact spec upload. Endpoints and
fields are traced to their spec through the parent node's `contentHash` (constitution: Spec
traceability).

## 2. EdgeOverride (table `edge_override`)

A human decision about a connection. It survives every re-sync.

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID | PK |
| `edgeKey` | text | Required. The full natural key (R6). No length cap, because it joins two endpoint keys |
| `edgeKeyHash` | char(64) | **Unique**. SHA-256 (hex) of `edgeKey`, used for lookups and uniqueness |
| `kind` | enum | `CONFIRMED`, `DISMISSED`, `MANUAL` |
| `sourceServiceKey` | string | Required |
| `targetServiceKey` | string | Required, MUST differ from `sourceServiceKey` (edge case: no self-connections) |
| `sourceEndpointKey` | string | Nullable. Null means a service-level edge (manual only) |
| `targetEndpointKey` | string | Nullable. Both endpoint keys are null or both are set |
| `snapshotMatches` | JSON | `FieldMatch[]` captured when the edge was confirmed or dismissed, for review of orphans |
| `note` | string(500) | Optional |
| `deletedAt` | timestamp | Null unless a `MANUAL` edge was deleted (soft delete, FR-041) |
| `createdAt`, `updatedAt` | timestamp | FR-041 |

### Rules

- `CONFIRMED` and `DISMISSED` MUST reference an edge key that is currently inferred **at the
  time of the request**. `MANUAL` MUST NOT duplicate an existing inferred or verified key: if
  the key is currently inferred, the API turns the request into `CONFIRMED`.
- Restoring a dismissed edge (edge case) means deleting the `DISMISSED` override.
- Deleting a verified edge (FR-022) means soft-deleting a `MANUAL` override (setting
  `deletedAt`), or changing `CONFIRMED` to `DISMISSED` (otherwise the edge would reappear as
  inferred). Soft-deleted rows are left out of the graph and of `GET /api/edges/overrides`.
  Drawing the same manual edge again clears `deletedAt`.
- There is **one row per `edgeKeyHash`**. Every transition (confirm, dismiss, draw, delete,
  restore) updates that row in place, setting `kind` and clearing `deletedAt` as needed; it never
  inserts a second row for the same key.

### State transitions (from the user's point of view)

```text
              confirm                     delete
 INFERRED ───────────────▶ CONFIRMED ───────────────▶ DISMISSED
    │  ▲                                                │  │
    │  └──────────────────── restore ───────────────────┘  │
    └─────────────────────────── delete ───────────────────┘

 (nothing) ── draw ──▶ MANUAL ── delete ──▶ MANUAL (deletedAt set, hidden) ── draw ──▶ MANUAL
```

**Orphaning** is computed when the graph is built and never stored: an override is
`orphaned` when any referenced service or endpoint key is absent from the current
`ServiceSpec` rows.

## 3. SavedScenario (table `saved_scenario`)

A named Draft Scenario drawn over the baseline.

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID | PK. Also the scenario ID in URLs |
| `name` | string(120) | Required. Case-insensitive uniqueness is enforced through `nameNormalized` |
| `nameNormalized` | string(120) | Required, **unique**. `lower(trim(name))`, set by the app on every write (portable across H2 and PostgreSQL, which differ on expression indexes) |
| `description` | string(2000) | Optional |
| `content` | JSON | `ScenarioContent` (below) |
| `baselineRef` | JSON | `{ serviceKey: contentHash }` captured at save time, for drift detection |
| `version` | integer | Optimistic lock. A stale `PUT` returns `409` (edge case: two tabs) |
| `createdAt`, `updatedAt` | timestamp | FR-041 |

### Embedded: ScenarioContent

| Field | Type | Rules |
|-------|------|-------|
| `proposedServices` | `ProposedService[]` | `{ tempKey, name, endpoints: ProposedEndpoint[] }`. `tempKey` is prefixed `proposed:` so it can never collide with baseline keys |
| `addedEndpoints` | `{ serviceKey, endpoint: ProposedEndpoint }[]` | New endpoints on **baseline** services (FR-031) |
| `addedFields` | `AddedField[]` | `{ serviceKey, endpointKey, direction: IN\|OUT, field: SimpleField }`. Proposed fields on **baseline** endpoints. Optional; defaults to `[]` so older exports still import. Fields on proposed services or added endpoints are stored on those endpoints directly |
| `proposedEdges` | `ProposedEdge[]` | `{ id, source, target, sourceHandle?, targetHandle?, label? }`. Source and target are baseline keys or `proposed:` keys, and source ≠ target |
| `positions` | `{ [nodeKey]: {x, y} }` | Scenario-only layout. Baseline nodes default to baseline positions |

`ProposedEndpoint` is `{ method, path, inputs?: SimpleField[], outputs?: SimpleField[] }`, where
`SimpleField` is `{ name, type? }`.

### Rules

- A scenario MUST NOT contain baseline mutations. It only adds items or overrides positions
  (Principle IV, FR-033).
- **Orphan check** on load or import (FR-038): every baseline `serviceKey`/`endpointKey` the
  scenario references (including `addedFields[].endpointKey`) must exist. Missing ones are listed in the response as
  `orphans: [{ ref, usedBy: [edgeId…] }]`.
- **Import** (FR-035): the document's `formatVersion` must be `1`. On a name clash, the import
  gets a suffix (`"Checkout v2 (imported)"`).

## 4. BaselineLayout (table `baseline_layout`, single row)

| Field | Type | Rules |
|-------|------|-------|
| `id` | smallint | Always `1` |
| `positions` | JSON | `{ [serviceKey]: {x, y} }`. Keys of deleted services are pruned on write |
| `version` | integer | Optimistic lock for `PUT /api/layout` |
| `updatedAt` | timestamp | |

## 5. InferenceSettings (table `inference_settings`, single row)

| Field | Type | Rules |
|-------|------|-------|
| `id` | smallint | Always `1` |
| `genericFields` | JSON `string[]` | Stored normalized. Seeded from `visualizer.inference.generic-fields` on first start |
| `maxTargetsPerField` | integer | Default `5`, range 1–50 |
| `minConfidence` | decimal | Default `0.4`, range 0–1 |
| `updatedAt` | timestamp | |

Changing settings bumps the graph generation, so inferred edges are recomputed (FR-016).

---

## Computed (not persisted) types

| Type | Definition |
|------|-----------|
| `FieldMatch` | `{ outputPath, inputPath, inputLocation, score }` |
| `InferredEdge` | `{ edgeKey, sourceEndpointKey, targetEndpointKey, confidence = max(score), matches: FieldMatch[], hiddenCandidates }` |
| `GraphResponse` | React Flow nodes and edges. See [contracts/openapi.yaml](./contracts/openapi.yaml) `GraphResponse` |
| `ParseError` | `{ location, message }` |

## Validation summary, mapped to requirements

| Rule | Requirement |
|------|-------------|
| Raw spec ≤ 10 MB; URL scheme `http` or `https` | FR-008 |
| No row on invalid spec; errors carry a location | FR-005 |
| Same `contentHash` means no-op | FR-006 |
| Failed re-sync keeps the last good `rawContent` | FR-007 |
| Normalized generic field names excluded before indexing | FR-016, Principle III |
| Override keys are natural, and orphans are computed rather than deleted | FR-023, SC-004, Principle II |
| `edgeKeyHash` unique; `edgeKey` uncapped | R6 |
| Manual-edge deletion is a soft delete that keeps `deletedAt` | FR-041 |
| Scenario names unique through `nameNormalized` | FR-034 |
| Nodes carry `contentHash` and `lastSyncAt` | Constitution: Spec traceability |
| No self-edges | Edge case |
| Scenario stores additions only, with its own positions | FR-033, Principle IV |
| `@Version` on scenario and layout | Edge case (two tabs) |
