# Contract: Module APIs and Allowed Dependencies (005)

The **external** contract does not change. The REST API stays exactly as described in
[`specs/004-meeting-tools/contracts/openapi.yaml`](../../004-meeting-tools/contracts/openapi.yaml),
and the connections document stays as in
[`connections-document.schema.json`](../../004-meeting-tools/contracts/connections-document.schema.json).
`ContractConformanceIT` keeps checking the 004 files.

This file is the **internal** contract: which modules exist, what each one exposes, and which other
modules it may use. `ModularityTest` enforces it through `allowedDependencies`.
Base package: `com.refinements.visualizer`.

## Allowed dependencies

```text
shared        (OPEN)  → —
workspace             → shared
note                  → shared
card                  → shared
microservice          → shared, workspace, card, card :: spi
connection            → shared, workspace, microservice, microservice :: spi, card :: model
layout                → shared, microservice, card
canvas                → shared, workspace, card, card :: model, microservice, connection, layout, note
scenario              → shared, canvas, card :: model, connection, note
```

A named interface is a separate dependency target in Spring Modulith: depending on `microservice` allows only its root API, so implementing `microservice.spi.ConnectionEndsPort` also needs `microservice :: spi` (same for `card :: spi`). `package-info.java` files copy this table as-is.

There are no cycles. At runtime there are two inverted call paths, both through SPI interfaces
that the lower module owns:
- microservice → connection, through `microservice.spi.ConnectionEndsPort`
- card → microservice, through `card.spi.CardLinkPort`

Note on `card :: model` and `SpecKeys`: today `connection` and `scenario` use
`ingestion.SpecKeys` (endpoint keys). `SpecKeys` moves to `card.model`, because it is pure
functions over spec value types.

## Public API per module

The signatures are indicative. The implementation may adjust parameter types, but it must not
change ownership or direction.

### shared (open)
`errors.*` (all exceptions, `DocumentError`, `ParseError`), `ApiExceptionHandler`, `PatchBody`,
`XYPosition`, `Names`, `VisualizerProperties`, configuration classes.

### workspace
- `WorkspaceRevision`: `long current()`, `long bump()` (MANDATORY tx), `void check(Long baseRevision)` (MANDATORY tx).

### note
- `NoteApi`: `List<NoteView> list()`.
- Published records: `NoteView`, `NoteKind`. (`NoteDto`, REST schema `Note`, lives in `adapter/in/web`.)

### card
- `SpecCardApi`:
  - `boolean exists(UUID)`
  - `List<SpecCardView> findAll()`
  - `Set<UUID> allIds()`
- Published records: `SpecCardView` (id, title, apiVersion, specFormat, sourceType, sourceUrl, contentHash, lastSyncAt, lastSyncStatus, endpoints, createdAt), `SourceType`, `SyncStatus`.
- Named interface `model`: `ParsedEndpoint`, `Field`, `FieldLocation`, `HttpMethod`, `SpecFormat`, `SpecKeys`.
- Named interface `spi`:
  - `CardLinkPort`:
    - `Optional<UUID> linkedMicroservice(UUID cardId)`
    - `Map<UUID, UUID> linksByCard()`
    - `void unlinkCard(UUID cardId)`
  - Implemented by microservice.

### microservice
- `MicroserviceApi`:
  - `MicroserviceView find(UUID)` (404 if missing)
  - `List<MicroserviceView> findAll()`
  - `List<MicroserviceView> findAllOrdered()` (by nameNormalized, for the canvas)
  - `Optional<MicroserviceView> findByNameNormalized(String)`
  - `MicroserviceView createWithoutRevision(String name, String description)`
  - `void describeWithoutRevision(UUID, String)`
  - `void ownWithoutRevision(UUID, String team, String repo, String docs)`
  - `List<UUID> reattach(UUID)`

  These are today's `createInternal`, `describeInternal`, `ownInternal` and `reattach`, used only by the connections document apply.
- Published records: `MicroserviceView` (id, name, nameNormalized, description, specCardId, team, repositoryUrl, docsUrl).
- Named interface `spi`:
  - `ConnectionEndsPort`:
    - `List<UUID> detach(UUID microserviceId)`
    - `void renamed(UUID microserviceId, String newName)`
    - `List<UUID> reattach(UUID microserviceId, String name, String nameNormalized)`
  - Implemented by connection.

### connection
- `ConnectionApi`: `List<ConnectionDto> findAll()`.
- `FlowApi`: `List<FlowDto> findAll()`.
- Published records and helpers: `ConnectionDto`, `FlowDto`, `ConnectionKind`, `FlowColor`, `ConnectionKey`, `ConnectionEndpoints` (match keys, used by canvas and scenario).

### layout
- `LayoutApi`: `LayoutView current()`, where `LayoutView` holds positions, collapsedCards and version.
- Key helpers: `LayoutKeys.msKey(UUID)`, `LayoutKeys.cardKey(UUID)` (today's static `BaselineLayout.msKey` and `cardKey`).

### canvas
- `CanvasApi`: `BaselineIndex index()`, used by scenario.
- Published records: `BaselineIndex`, `BaselineIndex.ConnectionRef`.

### scenario
- Nothing. Scenario is a top module and is only reachable through its REST adapter.

## Module layout (every module)

Template rules (fixed after reviewing the note module, 2026-11-23):
- **Root package = framework-free API only**: `<Module>Api` and the `*View` records / enums other modules use. No
  Swagger, JPA or Jackson annotations there. REST DTOs (with their `@Schema` names) live in `adapter/in/web` and map
  from the views. When another module's REST response embeds one of these views (for example `CanvasResponse.notes`),
  that response points at the owning module's published schema with
  `@ArraySchema(schema = @Schema(ref = "#/components/schemas/<Name>"))`, so the OpenAPI stays identical.
- **One inbound port per module**: `application/port/in/<Module>UseCases` (commands as nested records). The
  application service implements both `<Module>Api` and `<Module>UseCases` and is package-private.
- **Plain domain in every module** (small ones too): `domain/` has no JPA, Spring or Jackson. Persistence has
  `<Name>Entity` (package-private fields), `<Name>Mapper`, a package-private Spring Data repository and a
  `<Name>StoreAdapter` that copies domain state onto the managed entity (research R7).
- **Check**: diff `/v3/api-docs/api` and `GET /api/canvas` against the baseline after every module.

```text
<module>/
  package-info.java          @ApplicationModule(allowedDependencies = …)
  <Module>Api.java, *View records, enums   ← public API (root package), framework-free
  spi/                       @NamedInterface("spi") — ports another module implements (card, microservice only)
  model/                     @NamedInterface("model") — card only
  domain/                    business model and rules
  application/               @Application — use-case services implementing port.in, and the <Module>Api facade
    port/in/                 @PrimaryPort — <Module>UseCases (one interface)
    port/out/                @SecondaryPort — store / fetch / parse / other-module ports
  adapter/
    in/web/                  @PrimaryAdapter — controllers, request and response DTOs (@Schema names)
    out/persistence/         @SecondaryAdapter — *Entity, *Mapper, Spring Data repo, *StoreAdapter (every module)
    out/<other>/             @SecondaryAdapter — e.g. card: fetcher, parser; connection: microservice SPI impl
```
