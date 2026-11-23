# Data Model: Modular Backend (005)

**No schema change.** `backend/src/main/resources/db/migration/V1__schema.sql` stays the only migration and is
not edited (FR-003). Hibernate keeps `ddl-auto: validate`, so every new `*Entity` class must map to exactly
the same tables, columns and JSON column types as today. The validation fails at startup if one does not.

This document records, for each module, which type is the **domain model** and which is the
**persistence model**, and how they map.

## Ownership of tables

| Table | Owning module | Cross-module FK (unchanged) |
|---|---|---|
| `spec_card` | card | — |
| `microservice` | microservice | `spec_card_id → spec_card(id) ON DELETE SET NULL` |
| `flow` | connection | — |
| `connection` | connection | `source_id`, `target_id → microservice(id) ON DELETE SET NULL`; `flow_id → flow(id)` |
| `note` | note | — |
| `saved_scenario` | scenario | — |
| `baseline_layout` | layout | — (keys reference ids by string) |
| `workspace_state` | workspace | — |

Only the owning module's persistence adapter may read or write its table. Other modules go through that
module's API or SPI.

## Core modules: separate domain and persistence

### card
- **Domain** `card.domain.SpecCard`. State: id, sourceType, sourceUrl, title, apiVersion, specFormat, content, contentHash, endpoints (`List<ParsedEndpoint>`), lastSyncAt, lastSyncStatus, lastSyncErrors (`List<ParseError>`), createdAt, updatedAt. Behaviour (moved from today's entity):
  - `applyParsed(...)`
  - `markSynced(now)`
  - `markFailed(errors, now)`
- **Persistence** `card.adapter.out.persistence.SpecCardEntity`. Same columns as today's `SpecCard`, with the JSON columns for endpoints and errors.
- **Mapper** `SpecCardMapper`: entity ↔ domain.
- **Other outbound ports**:
  - `SpecSource.fetch(URI)`: adapter `HttpSpecFetcher`, today's `SpecFetcher`, including `validateUrl`.
  - `SpecParserPort.parse(content, base)`: adapter `SwaggerSpecParser`, today's `SpecParser` and `FieldFlattener`, `FieldNormalizer`.
  - `CardLinkPort`: SPI, implemented by microservice.

### microservice
- **Domain** `microservice.domain.Microservice`. State: id, name, nameNormalized, description, specCardId, team, repositoryUrl, docsUrl, createdAt, updatedAt. Behaviour:
  - `rename`
  - `describe`
  - `own`
  - `link`

  The validation rules move with it: `Ownership.team/url`, the name length and description length checks via `shared.Names`.
- **Persistence** `MicroserviceEntity` and `MicroserviceJpaRepository` (`findByNameNormalized`, `findBySpecCardId`, `findAllByOrderByNameNormalizedAsc`).
- **Other outbound ports**:
  - `ConnectionEndsPort`: SPI, implemented by connection.
  - `CardCatalog`: adapter that calls `card.SpecCardApi.exists`.
  - `Revision`: adapter that calls `workspace.WorkspaceRevision`.

### connection
- **Domain**:
  - `connection.domain.Connection`. State: id, name, nameNormalized, kind, description, flowId, sourceId, targetId, sourceName, targetName, the two normalized names, endpoint, step, timestamps. Behaviour: `setSource`, `setTarget`, `isDangling`, the patch rules.
  - `connection.domain.Flow`
  - `ConnectionKey`, `ConnectionEndpoints`, `FlowColor` as values.
  - Connections-document model, diff and changes (`ConnectionsDocumentModel`, `ConnectionsDocumentDiff`, `ConnectionsDocumentChanges`) are domain logic with no Jackson. They work on `MicroserviceView` instead of the `Microservice` entity.
  - JSON reading, validation-with-locations and writing (`ConnectionsDocumentReader`, `ConnectionsDocumentWriter`, `JsonLocator`) live in `adapter/out/document/` behind the `DocumentCodec` outbound port.
- **Persistence** `ConnectionEntity`, `FlowEntity`, and their repositories (`findDanglingByName`, `findBySourceIdOrTargetId`, `findAllByOrderByNameNormalizedAsc`).
- **Other outbound ports**:
  - `MicroserviceDirectory`: adapter that calls `microservice.MicroserviceApi`.
  - `DocumentCodec`: adapter in `adapter/out/document/` (Jackson-based reader and writer).
  - `Revision`

### scenario
- **Domain** `scenario.domain.Scenario`, which holds the `ScenarioContent` model records (`Proposed*`, `AddedEndpoint`, `AddedField`, `BaselineRef`, `ScenarioRemovals`, `ConnectionChange`, `SimpleField`). These are already plain records, so they move unchanged into `domain/`. `ScenarioValidator` is domain logic that takes a `BaselineIndex`.
- **Persistence** `SavedScenarioEntity`, which keeps today's JSON columns for content and baselineRef.
- **Other outbound ports**: `Baseline`, an adapter that calls `canvas.CanvasApi.index()`.

## Small modules: plain domain too (changed 2026-11-23 after the note review)

The small modules now follow the same split as the core ones: a plain `domain/` class, plus `*Entity`, `*Mapper`
and a store adapter in `adapter/out/persistence/`.

| Module | Domain (plain) | Store port (in `application/port/out`) | Persistence |
|---|---|---|---|
| note | `Note` (`create`, `restore`, `change`, text rule) | `NoteStore` (`findAllOldestFirst`, `findById`, `save`, `delete`) | `NoteEntity`, `NoteMapper`, `NoteJpaRepository`, `NoteStoreAdapter` — done |
| layout | `BaselineLayout` (positions, collapsed cards, `version`) | `LayoutStore` (`getSingleton`, `save` returning the new version) | `BaselineLayoutEntity` keeps the optimistic `version` column; the adapter must return the version Hibernate assigned |
| workspace | `WorkspaceState` (revision) | `WorkspaceStateStore` (`current`, `lockAndIncrement`, `lockAndGet`) | `WorkspaceStateEntity`, repository with the pessimistic lock query |

## Read records crossing module boundaries

The read records (`SpecCardView`, `MicroserviceView`, `ConnectionDto`, `FlowDto`, `NoteDto`, `LayoutView`, `BaselineIndex`) are immutable Java records. Each one is built inside the owning module's transaction, so it never carries a lazy-loaded proxy.

`ConnectionDto`, `FlowDto` and `NoteDto` are also the REST response shapes. They keep their simple names so that the springdoc schema names, and therefore `ContractConformanceIT`, do not change.

## Behaviours whose ordering must be preserved

| Behaviour | Today | After |
|---|---|---|
| Delete microservice | detach connection ends (keep names, null ids) → `flush` → delete row → bump revision | `ConnectionEndsPort.detach` (adapter flushes) → `MicroserviceStore.delete` → `Revision.bump` |
| Rename microservice | rename → update connection end names → `saveAndFlush` → reattach dangling ends → bump | same order via `ConnectionEndsPort.renamed` / `.reattach` |
| Delete card | unlink its microservice → `flush` → delete card | `CardLinkPort.unlinkCard` (adapter flushes) → `SpecCardStore.delete` |
| Apply connections document | `check(baseRevision)` (row lock) → create/describe/own microservices → replace flows/connections → bump | unchanged; microservice operations via `MicroserviceApi` in the same tx |
| Scenario save | reads baseline index, never bumps revision | unchanged |
