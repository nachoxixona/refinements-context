# Implementation Plan: Modular Backend (Modulith + Ports & Adapters)

**Branch**: `005-modular-backend` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-modular-backend/spec.md`

## Summary

Restructure the backend (about 7.4k lines, 10 packages) into a Spring Modulith modular monolith with 9 modules. Each module gets a ports-and-adapters layout. This is a pure refactor: the REST API, schema and frontend stay the same.

The work happens in this order:
1. Put the project under git with a baseline commit.
2. Add Spring Modulith, jMolecules and a `ModularityTest` that records today's violations.
3. Create `shared` and fold `ingestion` into `card`.
4. Break the two cycles with synchronous SPI ports owned by the lower module:
   - `microservice.spi.ConnectionEndsPort`
   - `card.spi.CardLinkPort`
5. Move `canvas` and `layout` onto module read APIs.
6. Convert one module at a time to the hexagonal layout, starting with `note`. The `connection`, `microservice`, `card` and `scenario` modules get plain domain models mapped at the persistence adapter.

The existing ITs, including `ContractConformanceIT`, and the Playwright e2e suite are the behavioural safety net. Details are in [research.md](./research.md), [data-model.md](./data-model.md) and [contracts/module-apis.md](./contracts/module-apis.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**:
- Already in use: Spring Boot 3.5.16 (web, validation, data-jpa, actuator), Flyway, swagger-parser 2.1.49, springdoc 2.8.17.
- Added: Spring Modulith 1.4.x (`starter-core`, `starter-test`) and jMolecules (`jmolecules-hexagonal-architecture`, `jmolecules-archunit` for tests).

**Storage**: PostgreSQL 16 (docker-compose volume `db-data`). The schema is not changed (`V1__schema.sql` only, `ddl-auto: validate`).

**Testing**:
- JUnit 5 and the Spring Boot test support.
- Testcontainers Postgres ITs (`PostgresIntegrationTest`).
- New: `ModularityTest` (`ApplicationModules.verify()` + `JMoleculesArchitectureRules.ensureHexagonal()` + a domain-purity ArchUnit rule), `@ApplicationModuleTest`, and pure domain tests.
- Frontend: Playwright e2e (unchanged).

**Target Platform**: A Linux container (root `Dockerfile` / `docker-compose.yml`) and the local JVM.

**Project Type**: A web application (Spring Boot backend and React frontend). Only the backend changes.

**Performance Goals**: `PerformanceSmokeIT` timings at most 10% slower than the baseline commit (SC-006).

**Constraints**:
- No API, schema or frontend change.
- Cross-module calls are synchronous and inside the caller's transaction.
- Every migration step is its own commit, and the build is green after it.
- The `db-data` volume, `backend/data` and `backend/data.backup-*` are never deleted.

**Scale/Scope**:
- 9 modules and about 85 main classes moved or split.
- About 7 JPA entities. 4 of them get separate domain classes and mappers.
- About 21 test classes need import updates.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
|---|---|---|
| I. Spec-first deterministic parsing | The parser and flattener move into `card.adapter.out.parser` unchanged. `SpecParserTest`, `FieldFlattenerTest`, `FieldNormalizerTest` and `SpecKeysTest` keep their assertions (FR-017). | ✅ |
| II. Declared connections | Connection semantics are unchanged: detach keeps names (dangling, not deleted) and document import stays all-or-nothing. Only the place where the code lives changes. `ConnectionIT`, `ConnectionsDocumentIT` and `MicroserviceLinkIT` guard this. | ✅ |
| III. Readable canvas layout | The layout save rules (prune keys, optimistic version) move into a `SaveLayout` use case unchanged. `LayoutAndCanvasIT` guards this. | ✅ |
| IV. Scenario sandbox isolation | Scenario still reads the baseline only through `CanvasApi.index()` and never bumps the revision. `ScenarioIT` guards this. | ✅ |
| V. Backend standards | Java 21 / Boot 3.x kept. The React Flow-shaped DTOs are unchanged. Flyway is kept. The JUnit parsing and document suites are kept. Constitution v2.1.0 (amended in T003) requires PostgreSQL everywhere and a Spring Modulith, ports-and-adapters backend, which is exactly what this feature builds. | ✅ |
| API contracts | The REST contract stays `specs/004-meeting-tools/contracts/openapi.yaml`, and `ContractConformanceIT` still runs against it. No DTO simple name changes. | ✅ |
| Quality gates | The backend tests must pass after every step. The frontend type-check and e2e tests must pass at the end. | ✅ |

**Post-design re-check (after Phase 1)**: The design adds no new external interface and no data change. It also adds no inferred edges. The constitution gates still pass. The one open item is the FR-020 amendment, done first (T003) so no restructuring happens under an outdated principle.

## Project Structure

### Documentation (this feature)

```text
specs/005-modular-backend/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R12
├── data-model.md        # Phase 1: domain vs persistence model per module, preserved orderings
├── quickstart.md        # Phase 1: validation guide
├── contracts/
│   └── module-apis.md   # Phase 1: internal module APIs + allowed dependencies (REST contract = 004, unchanged)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/refinements/visualizer/
├── VisualizerApplication.java
├── shared/            OPEN — errors/, ApiExceptionHandler, PatchBody, XYPosition, Names, VisualizerProperties, *Config, RequestSizeLimitFilter
├── workspace/         WorkspaceRevision (API) · domain/WorkspaceState · application/port/out/WorkspaceStateStore · adapter/out/persistence/
├── note/              NoteApi, NoteDto, NoteKind · domain/Note · application/{NoteService, port/in, port/out/NoteStore} · adapter/{in/web/NoteController, out/persistence/}
├── card/              SpecCardApi, SpecCardView, SourceType, SyncStatus
│   ├── model/         @NamedInterface — ParsedEndpoint, Field, FieldLocation, HttpMethod, SpecFormat, SpecKeys
│   ├── spi/           @NamedInterface — CardLinkPort
│   ├── domain/        SpecCard
│   ├── application/   SpecCardService · port/in (ImportCard, SyncCard, DeleteCard, ListCards) · port/out (SpecCardStore, SpecSource, SpecParserPort)
│   └── adapter/       in/web (SpecCardController, dto/) · out/persistence (SpecCardEntity, repo, mapper) · out/fetch (HttpSpecFetcher) · out/parser (SwaggerSpecParser, FieldFlattener, FieldNormalizer, ParsedSpec)
├── microservice/      MicroserviceApi, MicroserviceView
│   ├── spi/           @NamedInterface — ConnectionEndsPort
│   ├── domain/        Microservice, Ownership
│   ├── application/   MicroserviceService · port/in · port/out (MicroserviceStore, CardCatalog, Revision)
│   └── adapter/       in/web (MicroserviceController, dto/) · out/persistence · out/card (CardLinkAdapter implements card.spi.CardLinkPort, CardCatalogAdapter) · out/workspace
├── connection/        ConnectionApi, FlowApi, ConnectionDto, FlowDto, ConnectionKind, FlowColor, ConnectionKey, ConnectionEndpoints
│   ├── domain/        Connection, Flow, ConnectionSteps · document/ (Model, Diff, Changes — no Jackson)
│   ├── application/   ConnectionService, FlowService, ConnectionsDocumentService · port/in · port/out (ConnectionStore, FlowStore, MicroserviceDirectory, DocumentCodec, Revision)
│   └── adapter/       in/web (ConnectionController, FlowController, ConnectionsDocumentController, dto/) · out/persistence · out/document (Reader, Writer, JsonLocator) · out/microservice (ConnectionEndsAdapter implements microservice.spi.ConnectionEndsPort, MicroserviceDirectoryAdapter)
├── layout/            LayoutApi, LayoutView, LayoutKeys · domain/BaselineLayout · application/{SaveLayout, port/out/LayoutStore} · adapter/{in/web/LayoutController, out/persistence}
├── canvas/            CanvasApi, BaselineIndex · application/CanvasService (composes module APIs) · adapter/in/web/{CanvasController, dto/}
└── scenario/          domain/ (model records, ScenarioValidator) · application/{ScenarioService, port/out/{ScenarioStore, Baseline}} · adapter/{in/web, out/persistence, out/canvas}

backend/src/test/java/com/refinements/visualizer/
├── ModularityTest.java          # verify() + jMolecules hexagonal rules + domain purity + Documenter → docs/modules/
├── it/                          # unchanged assertions; imports updated
├── <module>/…                   # unit tests moved alongside their classes; new *DomainTest per core module
└── note/NoteModuleTest.java     # @ApplicationModuleTest template

docs/modules/                    # generated module diagrams (committed)
```

**Structure Decision**: This is the existing web-application layout (`backend/` + `frontend/`). Only the `backend/` package structure changes, as shown above. `frontend/` and `backend/src/main/resources/` are unchanged.

## Migration Sequence

Each step is one commit, and `./mvnw verify` must be green after it (SC-007).

| # | Step | ModularityTest |
|---|---|---|
| 0 | `git init`, `.gitignore` += `.claude/settings.local.json`; record PerformanceSmokeIT timings; constitution v2.1.0 (FR-020); baseline commit | — |
| 1 | Add Modulith + jMolecules deps; `ModularityTest` whose verify() test is `@Disabled("enabled in step 6")`, plus a test that logs `detectViolations()`; record the list in tasks.md | reports baseline |
| 2 | `common` → `shared` (open); move `Names` from connection to shared | — |
| 3 | Fold `ingestion` into `card` (`model/` named interface, parser/fetcher under `adapter/out`) | — |
| 4 | Break card ↔ microservice (`SpecCardApi`, `card.spi.CardLinkPort`) | — |
| 5 | Break microservice ↔ connection (`MicroserviceApi`, `microservice.spi.ConnectionEndsPort`) | — |
| 6 | Canvas and layout through module APIs; `package-info` `allowedDependencies` everywhere; **enable verify()** | ✅ passes |
| 7 | Hexagonal: **note** (template, + `@ApplicationModuleTest`) | ✅ |
| 8 | Hexagonal: workspace, layout | ✅ |
| 9 | Hexagonal + domain split: card | ✅ |
| 10 | Hexagonal + domain split: microservice | ✅ |
| 11 | Hexagonal + domain split: connection (incl. connections document) | ✅ |
| 12 | Hexagonal + domain split: scenario; canvas tidy-up | ✅ |
| 13 | Enable jMolecules hexagonal + domain-purity rules; Documenter → `docs/modules/` | ✅ all rules |
| 14 | Playwright e2e, live-data smoke check, perf comparison | — |

## Complexity Tracking

No constitution violations to justify.

The one deliberate cost is the domain/persistence mapping in 4 modules: about 4 mappers and about 4 duplicate entity classes. The user chose it to keep business rules free of framework concerns. The rejected simpler alternative was JPA-annotated domain classes everywhere.
