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
- `ConnectionApi`: `List<KeyedConnection> findAll()`, where `KeyedConnection` is a `ConnectionView` plus its stored `ConnectionKey`.
- `FlowApi`: `List<FlowView> findAllOrdered()`.
- Published records and helpers: `ConnectionView`, `KeyedConnection`, `FlowView`, `ConnectionKind`, `FlowColor`, `ConnectionKey`, `ConnectionEndpoints` (match keys, used by canvas and scenario). The REST shapes `ConnectionDto` ("Connection") and `FlowDto` ("Flow") live in `adapter/in/web`; `CanvasResponse` refs those schemas.

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
  application service implements both `<Module>Api` and `<Module>UseCases` and is package-private. A module with
  several aggregates has one `*UseCases` per aggregate (connection: `ConnectionUseCases`, `FlowUseCases`,
  `ConnectionsDocumentUseCases`), each implemented by its own service.
- **Plain domain in every module** (small ones too): `domain/` has no JPA, Spring or Jackson. Persistence has
  `<Name>Entity` (package-private fields), `<Name>Mapper`, a package-private Spring Data repository and a
  `<Name>StoreAdapter` that copies domain state onto the managed entity (research R7).
- **SPI implementations are inbound adapters**: when module A implements module B's SPI (for example microservice
  implements `card.spi.CardLinkPort`), the adapter lives in A's `adapter/in/<b>/` (`@PrimaryAdapter`) and delegates
  to an inbound port of A. That port is implemented by a **separate, small application service** that depends only
  on A's store (e.g. `CardLinkService`), never by A's main service: A's main service calls B, and B calls back
  into A, so sharing one bean makes a runtime bean cycle even though compile-time dependencies are acyclic.
  The `spi` packages themselves carry **no** jMolecules port annotation: Spring Modulith runs the jMolecules
  hexagonal rules, which forbid a `@PrimaryAdapter` depending on a `@SecondaryPort`.
- **Stores hand out copies**: a domain object loaded before another use case changed the same row (for example the
  connections document apply, where creating a microservice reattaches dangling ends) is stale. Reload it from the
  store before changing and saving it; saving copies *every* field onto the row.
- **Published rules**: a rule another module must apply identically (e.g. ownership limits used by the connections
  document reader) is published at the root as a framework-free class (`microservice.OwnershipRules`).
- **Records returned or accepted as-is**: value records a use case hands straight to the REST adapter (scenario
  content, `Orphan`, `ConnectionsDocumentChanges`, the canvas read model) live in `domain/` and keep their Swagger
  `@Schema` names and Bean Validation constraints, instead of being mirrored in `adapter/in/web`. The domain-purity
  rule forbids JPA, Spring web/data and Jackson, not these documentation and validation annotations.
- **Query modules**: canvas only composes other modules' views, so its application service calls their `*Api`
  interfaces directly instead of wrapping each in an outbound port. Its `domain/` is the read model
  (`CanvasResponse`, `CanvasNode`, …). Modules that change data (scenario included) reach other modules through
  outbound ports (scenario: `Baseline` → `CanvasApi.index()`).
- **Unique adapter class names**: Spring bean names come from the simple class name and are global, so an adapter
  class name must be unique across modules (connection's revision adapter is `ConnectionRevisionAdapter`, because
  microservice already has a `RevisionAdapter`). Two equal names fail every integration test at startup.
- **Check**: diff `/v3/api-docs/api` and `GET /api/canvas` against the baseline after every module.

```text
<module>/
  package-info.java          @ApplicationModule(allowedDependencies = …)
  <Module>Api.java, *View records, enums   ← public API (root package), framework-free
  spi/                       @NamedInterface("spi") — ports another module implements (card, microservice only); no port annotation
  model/                     @NamedInterface("model") — card only
  domain/                    business model and rules
  application/               @Application — use-case services implementing port.in, and the <Module>Api facade
    port/in/                 @PrimaryPort — <Module>UseCases (one interface)
    port/out/                @SecondaryPort — store / fetch / parse / other-module ports
  adapter/
    in/web/                  @PrimaryAdapter — controllers, request and response DTOs (@Schema names)
    out/persistence/         @SecondaryAdapter — *Entity, *Mapper, Spring Data repo, *StoreAdapter (every module)
    in/<other>/              @PrimaryAdapter — implementations of another module's SPI (e.g. connection: ConnectionEndsPort)
    out/<other>/             @SecondaryAdapter — e.g. card: fetcher, parser; connection: document codec, microservice API
```
