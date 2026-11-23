# Research: Modular Backend (005)

All Technical Context unknowns are resolved below. Each entry: **Decision**, **Rationale**, **Alternatives considered**.

## R1. Spring Modulith version

- **Decision**: Spring Modulith **1.4.x** (latest patch at implementation time, managed with the `spring-modulith-bom`), with `spring-modulith-starter-core` (compile) and `spring-modulith-starter-test` (test). Confirm the exact patch on Maven Central when adding it.
- **Rationale**: The 1.4 line targets Spring Boot 3.5, which the backend uses (3.5.16). Spring Modulith 2.x targets Boot 4 and would force a framework upgrade, which is out of scope.
- **Alternatives considered**: Plain ArchUnit rules only (no module model, no documentation, no `@ApplicationModuleTest`); Maven multi-module split (heavier build, still needs the same cycle breaking, harder to move one module at a time).

## R2. What counts as a module and its public API

- **Decision**: Each direct sub-package of `com.refinements.visualizer` is a module. The module's **root package is its public API**. Everything in sub-packages is internal. Exceptions are exposed with `@NamedInterface` on a `package-info.java`:
  - `<module>.spi`: outbound port interfaces that **another module implements** (e.g. `microservice.spi.ConnectionEndsPort`).
  - `card.model`: read-only spec value types (`ParsedEndpoint`, `Field`, `FieldLocation`, `HttpMethod`, `SpecFormat`) that canvas, connection and scenario need.
- Each module's `package-info.java` declares `@ApplicationModule(displayName = …, allowedDependencies = …)` so the allowed dependency list in [contracts/module-apis.md](./contracts/module-apis.md) is enforced, not just documented.
- **Rationale**: This is Spring Modulith's default convention, so `ApplicationModules.verify()` enforces it with no custom rules.
- **Alternatives considered**: Exposing `application/port/out` as a named interface. Rejected because it would also expose the module's own store ports to every other module.

## R3. The `shared` module

- **Decision**: `shared` is declared `@ApplicationModule(type = Type.OPEN)`. It contains `errors/*` (all exception types and `DocumentError`, `ParseError`), `ApiExceptionHandler`, `PatchBody`, `XYPosition`, `Names`, `JacksonConfig`, `OpenApiConfig`, `TimeConfig`, `RequestSizeLimitFilter` and `VisualizerProperties`. `shared` depends on no other module.
- **Rationale**: These are used by almost every module. An open module avoids exposing a dozen named interfaces. `Names` (name normalization) moves here because both microservice and connection need it, and keeping it in connection is part of today's cycle.
- **Alternatives considered**: Separate `config` and `errors` modules (no benefit at this size). Leaving `VisualizerProperties` in shared means card and connection read their own sub-properties from one record. That is acceptable; splitting it is a later option.

## R4. Breaking microservice ↔ connection

- **Decision**: `microservice.spi.ConnectionEndsPort` is implemented by `connection.adapter.out.microservice.ConnectionEndsAdapter`:
  - `List<UUID> detach(UUID microserviceId)`: keeps the stored end names, nulls the ids, flushes, and returns the affected connection ids (today's `delete` loop).
  - `void renamed(UUID microserviceId, String newName)`: updates the end names (today's `update` loop).
  - `List<UUID> reattach(UUID microserviceId, String name, String nameNormalized)`: today's `reattach`.
- The connection module reads microservices only through `microservice.MicroserviceApi` (`find`, `findAll`, `findByNameNormalized`, plus the internal create/describe/own operations the connections document needs) and the `MicroserviceView` read record.
- Compile-time dependency: connection → microservice only. At runtime, microservice calls connection through the port interface it owns.
- **Rationale**: These calls must run in the caller's transaction, must run *before* the microservice row is deleted (the FK is `ON DELETE SET NULL`, but the in-memory entities and the response ids must match), and their results feed the response (`danglingConnectionIds`, `reattachedConnectionIds`).
- **Alternatives considered**:
  - Spring Modulith `@ApplicationModuleListener`. Rejected because it is async and runs after commit, which changes the response and the atomicity.
  - Synchronous `@EventListener` events. Rejected because they hide control flow and cannot return ids cleanly.
  - Moving the logic into connection and letting connection orchestrate microservice deletes. Rejected because the delete use case belongs to microservice.

## R5. Breaking card ↔ microservice

- **Decision**: card is the lower module.
  - Microservice calls `card.SpecCardApi.exists(UUID)` when linking.
  - `card.spi.CardLinkPort` is implemented by `microservice.adapter.out.card.CardLinkAdapter`:
    - `Optional<UUID> linkedMicroservice(UUID cardId)`
    - `Map<UUID, UUID> linksByCard()`
    - `void unlinkCard(UUID cardId)`
- Card delete calls `unlinkCard` and then deletes the card in the same transaction, as today.
- **Rationale**: Same as R4. It is also the order used today (flush the unlink, then delete).
- **Alternatives considered**: Relying on the `ON DELETE SET NULL` FK only. Rejected because the persistence context would then hold a stale `specCardId`, and the behavior of the following reads would change.

## R6. Canvas and layout without foreign repositories

- **Decision**: Each lower module offers read operations on its API that return read records:
  - card: `SpecCardApi.findAll()` → `SpecCardView`, `allIds()`
  - microservice: `MicroserviceApi.findAll()` → `MicroserviceView`
  - connection: `ConnectionApi.findAll()` → `ConnectionDto`, `FlowApi.findAll()` → `FlowDto`
  - note: `NoteApi.list()` → `NoteDto`
  - layout: `LayoutApi.current()` → `LayoutView`
  - workspace: `WorkspaceRevision.current()`
- `CanvasService` composes these inside one read-only transaction, so it still reads a single consistent snapshot. `BaselineIndex` stays in canvas's API, because scenario uses it.
- `LayoutController` becomes `layout.adapter.in.web.LayoutController` plus a `SaveLayout` use case. The use case reads microservice ids, links and card ids through the microservice and card APIs.
- **Rationale**: This satisfies FR-012. Read records keep the canvas independent of entities. `ConnectionDto`, `FlowDto` and `NoteDto` stay the published REST shapes, so the springdoc schema names do not change.
- **Alternatives considered**: A shared read-only "query" module with SQL views. Rejected because it would bypass the module boundaries this feature is meant to create.

## R7. Domain model vs. JPA entity

- **Decision**:
  - **connection, microservice, card, scenario**: plain Java domain classes in `domain/` with no `jakarta.persistence`, `org.springframework.web` or Jackson annotations. JPA entities (`*Entity`) and Spring Data repositories live in `adapter/out/persistence/`, together with a hand-written `*Mapper`. The store adapter does `findById → copy state onto the managed entity → save`, so that Hibernate dirty checking, `@JdbcTypeCode(JSON)` columns and the flush order stay as they are.
  - **note, layout, workspace**: *changed 2026-11-23 after reviewing the note module* — these also get a plain `domain/` class with a `*Entity` and `*Mapper`, so every module has the same shape. The extra cost is small (three short entities).
- Mapping is hand-written (no MapStruct): there are about 7 entities, and new build plugins are not worth it.
- **Rationale**: This was the user's choice: full separation only where business rules live.
- **Risk and mitigation**: The order of flushes matters (detach before delete, unlink before delete, `saveAndFlush` before reattach). Store ports keep explicit `flush()` and `saveAndFlush` semantics, and the integration tests cover each case (`MicroserviceLinkIT`, `ConnectionIT`, `SpecCardIT`).
- **Alternatives considered**: Annotating domain classes with JPA (rejected by the user's choice); MapStruct (extra plugin, little gain).

## R8. Enforcing the internal hexagonal layout

- **Decision**: Use jMolecules (`org.jmolecules:jmolecules-hexagonal-architecture`, plus `jmolecules-archunit` in test scope). Put package-level annotations in each module:
  - `@Application` on `application`
  - `@PrimaryPort` on `application.port.in`
  - `@SecondaryPort` on `application.port.out` and `spi`
  - `@PrimaryAdapter` on `adapter.in`
  - `@SecondaryAdapter` on `adapter.out`

  The jMolecules hexagonal rule set forbids ports and the application from depending on adapters. A small extra ArchUnit rule in `ModularityTest` checks that `..domain..` packages of the four core modules import nothing from `jakarta.persistence`, `org.springframework.web` or `com.fasterxml.jackson` (FR-015).

  `ModularityTest` runs both `ApplicationModules.verify()` and `JMoleculesArchitectureRules.ensureHexagonal()`.
- **Rationale**: Spring Modulith integrates with jMolecules, and this turns "every module follows one layout" (FR-014) into a failing test instead of a review convention.
- **Alternatives considered**: Hand-written ArchUnit rules (more code to maintain); review only (erodes over time).

## R9. Transactions

- **Decision**: `@Transactional` stays on application services (use cases), as today. Adapters do not open transactions, with one exception: `WorkspaceRevision` keeps `Propagation.MANDATORY` on `bump()` and `check()`. Read-only use cases keep `readOnly = true`. `open-in-view` stays false.
- **Rationale**: Today's atomicity is preserved, including all-or-nothing document import and serialization on the revision row lock.

## R10. Module documentation

- **Decision**: `ModularityTest.writeDocumentation()` calls `new Documenter(modules).writeDocumentation()`, which writes PlantUML diagrams and module canvases. Those are then copied to `docs/modules/` (in the repository) by a small step in the same test, or with a Maven `resources` copy. The generated files are committed so they can be reviewed.
- **Rationale**: FR-019 requires documentation that is repeatable and generated from the code. `target/` is ignored by version control, so the output has to be copied out to be kept.

## R11. Tests and the safety net

- **Decision**:
  - The ITs keep their assertions. Only imports change, and only where tests reference moved classes (`Microservice`, `Connection`, repositories, `CanvasService`, `Names`, ingestion model types). ITs that set up data through entities switch to the new persistence entities or to `jdbc`.
  - `ContractConformanceIT` keeps checking against `specs/004-meeting-tools/contracts/openapi.yaml`; springdoc groups by path (`/api/**`), so moving controllers does not affect it.
  - DTO simple names must not change, because springdoc schema names come from them.
  - Unit tests of moved classes move with them.
  - New tests:
    - `ModularityTest`
    - one `@ApplicationModuleTest` for note (the template)
    - one pure domain test for each core module, using in-memory store fakes (SC-005)
- The frontend Playwright e2e suite runs against the restructured backend at the end (SC-002).
- **Performance baseline**: record the `PerformanceSmokeIT` timings on the baseline commit and compare them after migration (SC-006, ≤ 10% slower).

## R12. Version control baseline

- **Decision**: Run `git init` at the repository root (`/home/nacho/programacion/refinements-context`). The existing root `.gitignore` already excludes `target/`, `node_modules/`, `dist/`, `test-results/`, `backend/data/` and `backend/data.backup-*/`. Add `.claude/settings.local.json` to it. Make the baseline commit before any code change, then one commit per migration step (SC-007). Local only, with no remote.
- **Rationale**: FR-001 and SC-007.
