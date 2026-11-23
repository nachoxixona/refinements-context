# Data Model: Declared Connections & Spec Cards

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

```text
SpecCard 1 ──< Endpoint (embedded JSON) 1 ──< Field (embedded JSON)
Microservice 0..1 ── linked ── 0..1 SpecCard          (microservice.spec_card_id, unique)
Connection  >── source ──> Microservice (nullable FK, ON DELETE SET NULL; name kept → dangling)
Connection  >── target ──> Microservice (same)
BaselineLayout (single row) ── positions keyed by ms:{id} / card:{id}, collapsedCards
WorkspaceState (single row) ── revision, bumped by microservice and connection writes
SavedScenario ── references ──> msId / cardId / endpointKey / connectionId (may be orphaned)
MigrationReport (single row, optional) ── result of the one-time 002 migration
```

Normalization (`norm`) is `lower(trim(collapse internal whitespace))`. The app computes it on
every write, as `SavedScenario.nameNormalized` did in 002.

---

## 1. SpecCard (table `spec_card`)

A parsed API spec. It is created by import and never creates connections (FR-007).

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID | PK. Also the public card ID and the endpoint-key prefix |
| `title` | string(200) | Required. From `info.title` (FR-002) |
| `apiVersion` | string(50) | From `info.version`, nullable |
| `specFormat` | enum | `SWAGGER_2_0`, `OPENAPI_3_0`, `OPENAPI_3_1` |
| `sourceType` | enum | `PASTE`, `URL` (FR-009) |
| `sourceUrl` | string(2048) | Required when `sourceType = URL`. Scheme must be `http` or `https` |
| `rawContent` | text | Last successfully parsed spec, max 10 MB |
| `contentHash` | string(64) | SHA-256 of the normalized content. Used for traceability and duplicate detection (FR-006) |
| `endpoints` | JSON | `Endpoint[]`, as in 002 §1, with `endpointKey = {cardId}:{METHOD} {normalizedPath}` |
| `lastSyncAt`, `lastSyncStatus`, `lastSyncErrors` | | As in 002 (FR-004, FR-009) |
| `createdAt`, `updatedAt` | timestamp | |

**Rules**

- Importing content whose `contentHash` equals an existing card's, or a URL equal to an
  existing card's `sourceUrl`, returns `409 DUPLICATE_CARD` with `existingCardId`. Nothing is
  created (FR-006).
- **Re-sync** (URL cards only) updates the row in place. It keeps the `id`, so the link,
  position and scenarios survive (FR-004). A failed re-sync keeps the last good content.
- **Delete** removes the row and nulls out the linked microservice's `spec_card_id`. Connections
  are untouched (FR-007, FR-026).

## 2. Microservice (table `microservice`)

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID | PK. Stable across renames |
| `name` | string(120) | Required, non-blank |
| `nameNormalized` | string(120) | **Unique** (FR-011) |
| `description` | string(2000) | Nullable (FR-010, FR-021a) |
| `specCardId` | UUID | Nullable, **unique**, FK → `spec_card.id` `ON DELETE SET NULL` (FR-012) |
| `createdAt`, `updatedAt` | timestamp | |

**Rules**

- **Create**: if the name clashes with an existing microservice → `409 DUPLICATE_NAME`. After
  creating it, every **dangling** connection end whose normalized name equals the new name is
  re-attached (research R2).
- **Rename**: a clash → `409`. Otherwise it updates `sourceName`/`targetName` (and their
  normalized copies) of every connection that references the microservice, in the same transaction
  (FR-028).
- **Link** (`PUT /microservices/{id}/card`):
  - the microservice already has a card → `409 MICROSERVICE_HAS_CARD`, unless `replace=true`,
    in which case the old card becomes unlinked;
  - the card is linked to another microservice → `409 CARD_ALREADY_LINKED`. Replacement is not
    offered in this direction (FR-013).
- **Unlink**: sets `specCardId` to null. The client places the card next to the microservice
  (research R8).
- **Delete**: removes the row. Its connections' IDs become null through the FK, so those connections are
  now dangling (FR-016, FR-027). The linked card becomes unlinked.

## 3. Connection (table `connection`)

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID | PK. Kept when an applied document matches the connection (research R5) |
| `name` | string(120) | Required, non-blank (FR-017) |
| `nameNormalized` | string(120) | |
| `kind` | enum | `REQUEST`, `EVENT`, or null (FR-017) |
| `description` | string(2000) | Nullable |
| `sourceId`, `targetId` | UUID | Nullable FK → `microservice.id` `ON DELETE SET NULL` |
| `sourceName`, `targetName` | string(120) | Required. Always equal to the current microservice name while attached |
| `sourceNameNormalized`, `targetNameNormalized` | string(120) | Required |
| `createdAt`, `updatedAt` | timestamp | |

**Constraints**

- `UNIQUE (source_name_normalized, target_name_normalized, name_normalized)`. Several connections
  per pair are allowed in either direction, with names unique per source → target
  (FR-017, research R3).
- `CHECK (source_name_normalized <> target_name_normalized)`: no connection from a microservice to
  itself.

**State**

```text
              delete source/target microservice
 attached ───────────────────────────────────────▶ dangling
    ▲                                                 │
    └──── recreate (create microservice with that name) or reassign ┘
 (either state) ── delete connection / document without it ──▶ (removed)
```

A connection is *dangling* when `sourceId IS NULL OR targetId IS NULL`. Dangling connections are listed in
`CanvasResponse.danglingConnections`, are not drawn on the canvas, and are never deleted
automatically (FR-027).

- **Reassign** (`POST /connections/{id}/reassign`) sets the missing end to an existing
  microservice. The uniqueness rule still applies (`409 DUPLICATE_CONNECTION`).
- **Create and update** from the canvas apply the same rules as the document validator
  (self connection, uniqueness, lengths, kind).

## 3b. Flow (table `flow`)

| Field | Type | Rules |
|-------|------|-------|
| `id` | UUID | PK |
| `name` | string(120) | Required, non-blank |
| `nameNormalized` | string(120) | **Unique** (Names.norm) |
| `color` | enum | `BLUE`, `PURPLE`, `ORANGE`, `PINK`, `AMBER`, `BROWN`, `CYAN`, `SLATE`. Green and red are reserved for proposed and removed elements (FR-049). Written lowercase in the connections document |
| `createdAt`, `updatedAt` | timestamp | |

`Connection.flowId` is a nullable FK → `flow.id` `ON DELETE SET NULL` (FR-050). Flow writes bump
`WorkspaceState.revision`. Connections document (§4): optional `flows[]` of `{name, color}`; `connections[].flow`
names a listed flow (`UNKNOWN_FLOW` otherwise), `INVALID_COLOR` and `DUPLICATE_FLOW` are errors;
applying creates missing flows and recolors listed ones, never deletes flows (FR-052).
Scenario content: `ProposedConnection.flowId` and `ConnectionChange.flowId` (a flow id, or `NONE` to ungroup);
missing flow ids are orphans (FR-053).

## 3c. Connection.endpoint (FR-054 – FR-056)

| Field | Type | Rules |
|-------|------|-------|
| `Connection.endpoint` | string(600) | Nullable. `METHOD /path`: an HTTP method (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`, `TRACE`, upper-cased on write), one space, a path starting with `/`. Stored as text, independent of the spec |

Computed, not stored: `ConnectionEdgeData.endpointMissing` is true when the target microservice has a linked
card and no endpoint of that card has the same method and normalized path (`SpecKeys.normalizePath`).
Connections document: `connections[].endpoint` (string); a malformed value is `INVALID_ENDPOINT`. Scenario content:
`ProposedConnection.endpoint`, and `ConnectionChange.endpoint` (a value, or `NONE` to clear).

## 4. Connections Document (not stored; derived from §2 and §3)

The wire format is specified in [research R4](./research.md#r4-connections-document-format-v1) and
in [contracts/connections-document.schema.json](./contracts/connections-document.schema.json).

**Validation errors** (research R6) are returned all together, in document order:

| Kind | Example `path` | Rule |
|------|----------------|------|
| `SYNTAX` | — (line and column only) | Not valid JSON |
| `UNSUPPORTED_VERSION` | `formatVersion` | Missing, or not `1` |
| `WRONG_TYPE` | `connections` | `connections` is not an array, a name is not a string, … |
| `MISSING` | `connections[3].target` | Required property absent or blank |
| `TOO_LONG` | `connections[0].name` | Over 120 characters (names) or 2000 (descriptions) |
| `INVALID_KIND` | `connections[2].kind` | Not `request` or `event` |
| `SELF_CONNECTION` | `connections[5]` | `norm(source) = norm(target)` |
| `DUPLICATE_CONNECTION` | `connections[7]` | Same `(source, target, name)` as an earlier entry (named in the message) |
| `DUPLICATE_MICROSERVICE` | `microservices[4].name` | Same normalized name as an earlier entry |
| `UNKNOWN_PROPERTY` | `connections[1].lable` | Not in the format |
| `TOO_MANY` | `connections` | Over 5,000 connections or 2,000 microservices |

The document must also be at most 2 MB; a larger one returns `413`.

**Export**: the export lists every microservice and every attached connection. Dangling connections
(§3) are left out and counted in `excludedDanglingConnections`. Re-importing such an export
therefore lists them under `connectionsRemoved` (research R4, R5).

**Diff** (`ConnectionsDocumentChanges`): `microservicesAdded[]`, `microservicesChanged[]`
(description old → new), `connectionsAdded[]`, `connectionsChanged[]` (kind and description old → new),
`connectionsRemoved[]`, and `unchanged` (true when everything is empty).

## 5. BaselineLayout (table `baseline_layout`, single row; extended from 002)

| Field | Type | Rules |
|-------|------|-------|
| `positions` | JSON | `{ "ms:{id}" \| "card:{id}": {x, y} }`. Only unlinked cards have a `card:` position; docked cards are positioned relative to their microservice (research R8). Keys of deleted entities are pruned on write |
| `collapsedCards` | JSON `uuid[]` | Docked cards shown as a badge (FR-014). New column |
| `version` | integer | Optimistic lock, as in 002 |

## 6. WorkspaceState (table `workspace_state`, single row)

| Field | Type | Rules |
|-------|------|-------|
| `id` | smallint | Always `1` |
| `revision` | bigint | Incremented by every microservice or connection write and every applied document. Sent as `baseRevision` by the document editor; a stale value returns `409` (research R7) |

## 7. SavedScenario (table `saved_scenario`; content v2)

The table is unchanged from 002 §3. `content` holds **ScenarioContent v2**
([research R9](./research.md#r9-scenario-content-v2)), and `baselineRef` is
`{ cards: {cardId: contentHash}, revision }`.

| Property | Rules |
|----------|-------|
| `proposedMicroservices[]` | `tempKey` matches `^proposed:`. The name must not clash (normalized) with a baseline microservice or another proposal. Optional proposed `card` (FR-037) |
| `proposedLinks[]` | The microservice (msId or tempKey) must have no card, in the baseline or the proposal, unless that link is in `removals.links`. The card must be unlinked, or its link must be in `removals.links` |
| `proposedConnections[]` | Same rules as Connection §3, checked against baseline connections plus other proposals. Source and target are an msId or a tempKey |
| `addedEndpoints[]`, `addedFields[]` | As in 002, keyed by `cardId` and `endpointKey` (FR-037) |
| `removals` | `connections[]`, `microservices[]` and `links[]` (msIds) of the **baseline** (FR-037a). The client derives a microservice removal's connections and link for display; it does not store them |
| `connectionChanges[]` | `{ connectionId, name?, kind?: REQUEST \| EVENT \| NONE, description? }` on baseline connections. A new name must keep uniqueness |
| `positions` | Scenario-only positions keyed like BaselineLayout, or by `tempKey` |

- **Orphans** (FR-042): any referenced msId, cardId, endpointKey or connectionId that is missing is
  returned as `orphans: [{ ref, usedBy[] }]`. The scenario element is kept and flagged.
- **Import**: `formatVersion` 2 is taken as is; `formatVersion` 1 is converted by
  `LegacyScenarioConverter` (research R10) and the response carries `conversionNotes[]`.
- A scenario never writes to §1–§6 (Principle IV, FR-039).

## 8. MigrationReport (table `migration_report`, at most one row)

| Field | Type | Rules |
|-------|------|-------|
| `id` | smallint | Always `1` |
| `ranAt` | timestamp | |
| `counts` | JSON | `{ microservices, cards, connections, scenarios, positions, skippedDeletedEdges, discardedDismissals, discardedSettings }` |
| `notes` | JSON `string[]` | Renamed duplicates, dropped handles, orphaned scenario elements (FR-045) |
| `acknowledged` | boolean | Set when the user closes the summary, so it is not shown again (FR-047) |

## 9. Removed and legacy tables

The 002 tables are renamed by `V6` and kept, read-only, for one release (research R10):

| 002 table | Renamed to |
|-----------|-----------|
| `service_spec` | `legacy_service_spec` |
| `edge_override` | `legacy_edge_override` |
| `inference_settings` | `legacy_inference_settings` |

No code reads these tables after the migration.

## Validation summary, mapped to requirements

| Rule | Requirement |
|------|-------------|
| Import never touches `connection` | FR-007, SC-007 |
| `contentHash` or `sourceUrl` duplicate → 409 | FR-006 |
| `microservice.nameNormalized` unique | FR-011 |
| `microservice.specCardId` unique (1:1) and explicit replace | FR-012, FR-013 |
| FK `ON DELETE SET NULL` plus kept names → dangling, never deleted | FR-016, FR-027 |
| Rename cascades to connection names in one transaction | FR-028 |
| Connection uniqueness per (source, target, name); no self connection; kind enum | FR-017, FR-023 |
| Whole-document validation with positions; all-or-nothing apply | FR-023, SC-004 |
| Replace semantics; microservices never removed by a document | FR-021, FR-021a |
| Deterministic export lists every microservice | FR-025, SC-003 |
| `revision` guard on editor save | Edge case (two tabs) |
| `collapsedCards` persisted | FR-014 |
| Scenario v2 removals and changes stored only in the scenario | FR-037a, FR-039 |
| Migration runs once (Flyway history) and leaves a report | FR-044 – FR-047 |
