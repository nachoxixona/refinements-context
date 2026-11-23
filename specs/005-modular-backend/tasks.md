# Tasks: Modular Backend (Modulith + Ports & Adapters)

**Input**: spec.md, plan.md, research.md (R1–R12), data-model.md, contracts/module-apis.md, quickstart.md

**Tests**: These are included because the spec requires them. They cover the modularity check (FR-013, SC-003), domain tests without infrastructure (SC-005), the note module test (template), and the unchanged existing suite (FR-004).

**Paths**:
- `B/` = `backend/src/main/java/com/refinements/visualizer/`
- `T/` = `backend/src/test/java/com/refinements/visualizer/`

**Rules for every task**:
- Pure refactor. Do not change any REST path, JSON shape, status code, error message or DTO simple name (FR-002, research R11).
- Never edit `V1__schema.sql` (FR-003).
- Never edit test assertions. Only imports and data-setup code that refers to moved classes may change (FR-004).
- **Visibility**: moving a class into a sub-package breaks package-private access (e.g. `WorkspaceState.increment()` is called from `WorkspaceRevision`). Widen to `public` only on types in module-internal packages (`domain/`, `application/`, `adapter/`); never add members to a module's root API just to make a move compile.
- `./mvnw verify` (from `backend/`) must be green before each commit (SC-007).
- Never run `docker compose down -v`. Never delete `backend/data` or `backend/data.backup-*`.

**Commit convention**: Each phase checkpoint marked 🔖 is one git commit (plan.md "Migration Sequence").

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

**Purpose**: Put the project under version control and record the baseline (FR-001, R12).

- [X] T001 Add `.claude/settings.local.json` to the root `.gitignore`. Then run `git init` in the repository root, and with `git status --ignored` check that `backend/data/`, `backend/data.backup-*/`, `target/`, `node_modules/`, `dist/` and `test-results/` are ignored.
- [X] T002 Run `./mvnw verify` in `backend/` on the untouched code. Confirm it is green and write down the `PerformanceSmokeIT` timings in `specs/005-modular-backend/baseline.md` (SC-006 reference). Also save `GET /api/canvas` and `GET /api/connections-document` from the running app against the live `db-data` DB to a scratch location outside the repo, for quickstart §7.
- [X] T003 Run `/speckit-constitution` to amend Principle V (FR-020) **before any restructuring**: replace "H2 for local development" with PostgreSQL via docker compose (one Flyway schema script), and add the backend architecture rule — Spring Modulith modules, ports and adapters inside each, `ModularityTest` as a quality gate. MINOR bump to 2.1.0 with a Sync Impact Report. Then update plan.md's Constitution Check row for Principle V to ✅ without the amendment note.
- [ ] T004 🔖 Make the baseline commit "Baseline before 005 modular backend" containing everything not ignored, including the amended `.specify/memory/constitution.md`.

---

## Phase 2: Foundational (blocks all stories)

**Purpose**:
- Add the tooling.
- Create the `shared` module and fold `ingestion` into `card`.
- Record today's violations.

These steps move packages only; behaviour does not change.

- [ ] T005 Update `backend/pom.xml`. Import `org.springframework.modulith:spring-modulith-bom` 1.4.x (latest patch; R1) in `dependencyManagement`. Add `spring-modulith-starter-core`, `org.jmolecules:jmolecules-hexagonal-architecture` and, in test scope, `spring-modulith-starter-test` and `org.jmolecules.integrations:jmolecules-archunit` (R8). Confirm the versions on Maven Central and that they are compatible with Boot 3.5.16.
- [ ] T006 Create `T/ModularityTest.java`:
  - `ApplicationModules.of(VisualizerApplication.class)`
  - a `verifiesModularStructure()` test that calls `modules.verify()`, marked `@Disabled("enabled in T030")`
  - a `logsViolations()` test that prints `modules.detectViolations()`

  Run it and paste the reported violations into the "Baseline violations" section at the end of this file.
- [ ] T007 Move `B/common/**` into `B/shared/**`: `errors/` keeps its sub-package, and the other classes go to the `shared` root. Add `B/shared/package-info.java` with `@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared")` (R3). Update all imports in main and test code.
- [ ] T008 Move `B/connection/Names.java` → `B/shared/Names.java` and `T/connection/NamesTest.java` → `T/shared/NamesTest.java`. Update imports (R3).
- [ ] T009 Fold `ingestion` into `card` (R2 and data-model.md card):
  - Move `B/ingestion/model/*` (`ParsedEndpoint`, `Field`, `FieldLocation`, `HttpMethod`, `SpecFormat`) and `B/ingestion/SpecKeys.java` → `B/card/model/`. Add `B/card/model/package-info.java` with `@NamedInterface("model")`.
  - Move `SpecFetcher` → `B/card/adapter/out/fetch/`.
  - Move `SpecParser`, `ParsedSpec`, `FieldFlattener` and `FieldNormalizer` → `B/card/adapter/out/parser/`.
  - Delete the empty `ingestion` package.
- [ ] T010 [P] Move the ingestion unit tests to match T009. `SpecParserTest`, `FieldFlattenerTest` and `FieldNormalizerTest` go to `T/card/adapter/out/parser/`, and `SpecKeysTest` goes to `T/card/model/`. Assertions stay unchanged (Principle I, FR-017).
- [ ] T011 🔖 Run `./mvnw verify`; everything must be green. Commit "005: add Modulith, shared module, fold ingestion into card".

**Checkpoint**: The `shared` and `card.model` modules exist. Two cycles and the cross-module repository reads remain.

---

## Phase 3: User Story 1 — Enforced boundaries (P1) 🎯 MVP

**Goal**: Remove both cycles and every cross-module repository or entity access, then turn on `verify()` (FR-006 to FR-013).

**Independent Test**: Follow quickstart §2 and §3. `ModularityTest` passes with 0 violations and fails when a forbidden dependency is injected. All existing tests stay green.

### card ↔ microservice (R5)

- [ ] T012 [US1] Create the read side of card's public API:
  - `B/card/SpecCardView.java`: a record with the fields listed in contracts/module-apis.md §card.
  - `B/card/SpecCardApi.java`: an interface with `exists`, `findAll` (ordered like today's canvas: createdAt, then id) and `allIds`.
  - The implementation: `SpecCardService`, or a dedicated `SpecCardQueries` `@Service` in `B/card/`.

  Move `SourceType` and `SyncStatus` to the `card` root if they are not already there.
- [ ] T013 [US1] Create `B/card/spi/CardLinkPort.java` (`linkedMicroservice`, `linksByCard`, `unlinkCard`) and `B/card/spi/package-info.java` with `@NamedInterface("spi")`. Change `B/card/SpecCardService.java` to use `CardLinkPort` instead of `MicroserviceRepository`/`Microservice`:
  - `list()` uses `linksByCard`
  - `sync()` uses `linkedMicroservice`
  - `delete()` calls `unlinkCard`, which must flush, and only then deletes the card (data-model.md "Behaviours whose ordering must be preserved": Delete card)
- [ ] T014 [US1] Implement the port in `B/microservice/CardLinkAdapter.java`. It is a `@Component` that implements `card.spi.CardLinkPort` with `MicroserviceRepository`. `unlinkCard` does `findBySpecCardId → link(null, now) → flush`. It moves to `adapter/out/card/` in T041.
- [ ] T015 [US1] In `B/microservice/MicroserviceService.java`, replace `SpecCardRepository.existsById` with `SpecCardApi.exists`.

### microservice ↔ connection (R4)

- [ ] T016 [US1] Create the read side of microservice's public API:
  - `B/microservice/MicroserviceView.java`: a record with the fields from data-model.md microservice, plus a static `from(Microservice)`.
  - `B/microservice/MicroserviceApi.java` (contracts/module-apis.md §microservice): `find`, `findAll`, `findAllOrdered` (by nameNormalized), `findByNameNormalized`, `createWithoutRevision`, `describeWithoutRevision`, `ownWithoutRevision`, `reattach`.

  `MicroserviceService` implements the interface. The `*WithoutRevision` methods replace `createInternal`, `describeInternal` and `ownInternal`, and take ids, not entities.
- [ ] T017 [US1] Create `B/microservice/spi/ConnectionEndsPort.java` (`detach`, `renamed`, `reattach`) and `B/microservice/spi/package-info.java` with `@NamedInterface("spi")`. Change `MicroserviceService` so it no longer uses `ConnectionRepository`/`Connection`:
  - `update`: the rename loop becomes `renamed`
  - `reattach`: becomes `ConnectionEndsPort.reattach`
  - `delete`: becomes `detach`, then delete, then bump

  Keep the order from data-model.md "Behaviours whose ordering must be preserved", including `saveAndFlush` before reattach. Response ids must stay identical (`MicroserviceMutation.reattachedConnectionIds`, `MicroserviceDeleted.danglingConnectionIds`).
- [ ] T018 [US1] Implement the port in `B/connection/ConnectionEndsAdapter.java`. It is a `@Component` that implements `microservice.spi.ConnectionEndsPort` by moving today's loops from `MicroserviceService`. `detach` must call `connections.flush()` before returning. It moves to `adapter/out/microservice/` in T049.
- [ ] T019 [US1] Make `B/connection/ConnectionService.java` and `B/connection/document/ConnectionsDocumentService.java` use `MicroserviceApi`/`MicroserviceView` instead of `MicroserviceRepository`, `MicroserviceService` and the `Microservice` entity. Then do the same in `ConnectionsDocumentDiff`, `ConnectionsDocumentWriter` and `ConnectionsDocumentModel`, which work on `MicroserviceView` (data-model.md connection). Document apply stays in one transaction: `check(baseRevision)` → microservice operations → connections → bump.
- [ ] T020 [US1] Move the connection API surface to the `B/connection/` root: `ConnectionDto` and `FlowDto` (simple names unchanged, R11), `ConnectionKind`, `FlowColor`, `ConnectionKey` and `ConnectionEndpoints`. Create `ConnectionApi.findAll()` and `FlowApi.findAll()`, implemented by `ConnectionService`/`FlowService`, returning DTOs (contracts/module-apis.md §connection).

### canvas, layout, note, scenario through APIs (R6)

- [ ] T021 [P] [US1] Create `B/note/NoteApi.java` (`list()`), implemented by `NoteService`. `NoteDto` and `NoteKind` stay at the `note` root.
- [ ] T022 [US1] Create `B/layout/LayoutApi.java` (`current()` → `B/layout/LayoutView.java` record: positions, collapsedCards, version) and `B/layout/LayoutKeys.java` (`msKey`, `cardKey`, moved from `BaselineLayout`).
- [ ] T023 [US1] Move the save logic from `B/layout/LayoutController.java` into `B/layout/LayoutService.java`, which implements `LayoutApi` and has `save(LayoutUpdate)`. It reads microservice ids and links through `MicroserviceApi.findAll()` and card ids through `SpecCardApi.allIds()`. The pruning, collapse filter and stale-version error stay exactly as today. The controller only delegates.
- [ ] T024 [US1] Rewrite `B/canvas/CanvasService.java` so it uses only `SpecCardApi`, `MicroserviceApi`, `ConnectionApi`, `FlowApi`, `LayoutApi`, `NoteApi` and `WorkspaceRevision`. Keep it in one `@Transactional(readOnly = true)` and keep the node, edge and dangling ordering and the `endpointMissing` logic identical. `index()` builds `BaselineIndex` from the same APIs.
- [ ] T025 [US1] Create `B/canvas/CanvasApi.java` (`index()`), implemented by `CanvasService`. Make `B/scenario/ScenarioService.java` depend on `CanvasApi` instead of `CanvasService`. `BaselineIndex` stays at the `canvas` root.
- [ ] T026 [US1] Fix the imports in scenario (`ScenarioValidator` and the model records) so they use only the published types: `connection` root, `card.model`, `note` root and `canvas` root.

### Enforce

- [ ] T027 [P] [US1] Add a `package-info.java` with `@ApplicationModule(displayName = …, allowedDependencies = {…})` to each module (contracts/module-apis.md "Allowed dependencies", with named interfaces written as `"card :: model"`, `"card :: spi"`, `"microservice :: spi"`):
  - `B/workspace/package-info.java`
  - `B/note/package-info.java`
  - `B/card/package-info.java`
  - `B/microservice/package-info.java`
  - `B/connection/package-info.java`
  - `B/layout/package-info.java`
  - `B/canvas/package-info.java`
  - `B/scenario/package-info.java`
- [ ] T028 [US1] Update the test imports and setup that refer to moved or now-internal types, without touching assertions. Tests are outside module verification, so `it/` may keep using repositories. These tests refer to them: `T/it/*IT.java`, `T/canvas/CanvasServiceTest.java`, `T/connection/document/*Test.java`.
- [ ] T029 [US1] Run `./mvnw test -Dtest=ModularityTest#logsViolations`. Fix any remaining violations in main code until the list is empty.
- [ ] T030 [US1] Remove the `@Disabled` from `verifiesModularStructure()` in `T/ModularityTest.java`, then run `./mvnw verify`. Everything must be green, including `ContractConformanceIT`.
- [ ] T031 [US1] Check that the boundary check catches violations (quickstart §3). Temporarily inject `MicroserviceRepository` into `CanvasService`, confirm `ModularityTest` fails, then revert. Record the result in "Baseline violations" below.
- [ ] T032 🔖 [US1] Commit "005: break module cycles, enforce Modulith boundaries".

**Checkpoint**: The MVP is done. The backend has 9 enforced, acyclic modules and unchanged behaviour.

---

## Phase 4: User Story 2 — Ports and adapters per module (P2)

**Goal**: Every module follows the same layout (FR-014 to FR-018). Each module is its own commit.

**Independent Test**: For each module, its `*DomainTest` runs without Spring or Docker. `./mvnw verify` stays green, and after T056 the jMolecules rules pass.

### note — the template (FR-018)

- [ ] T033 [US2] Restructure `B/note/` following plan.md "Source Code":
  - `domain/Note.java` (the entity stays the model, data-model.md "Small modules")
  - `application/NoteService.java` with `port/in/` use-case interfaces (`ListNotes`, `CreateNote`, `UpdateNote`, `DeleteNote`) and `port/out/NoteStore.java`
  - `adapter/in/web/NoteController.java`
  - `adapter/out/persistence/NoteJpaRepository.java` and `NoteStoreAdapter.java`

  Add package-info annotations: `@Application`, `@PrimaryPort`, `@SecondaryPort`, `@PrimaryAdapter`, `@SecondaryAdapter` (R8). `NoteApi`, `NoteDto`, `NoteWrite` and `NoteKind` stay at the root. Keep `NoteWrite` at the root only if the controller is its only user; otherwise move it to `adapter/in/web`.
- [ ] T034 [P] [US2] Add `T/note/NoteModuleTest.java` with `@ApplicationModuleTest` and Testcontainers Postgres (reuse the `@ServiceConnection` pattern from `T/it/PostgresIntegrationTest.java`). It creates, lists and deletes a note through the use cases.
- [ ] T035 🔖 [US2] Commit "005: note module — ports and adapters (template)".

### workspace, layout

- [ ] T036 [P] [US2] Restructure `B/workspace/`:
  - `domain/WorkspaceState.java`
  - `application/port/out/WorkspaceStateStore.java` (`find`, `lockSingleton`)
  - `adapter/out/persistence/` (repository and adapter)

  `WorkspaceRevision` stays at the root as the API and keeps `Propagation.MANDATORY` on `bump()` and `check()` (R9). `WorkspaceState.increment()` becomes `public` (visibility rule). Update `T/it/*` imports of `WorkspaceRevision` if needed. The stale-revision guard is `ConnectionsDocumentIT` (stale `baseRevision` → 409); if no test there covers it, add one before this change.
- [ ] T037 [P] [US2] Restructure `B/layout/`:
  - `domain/BaselineLayout.java` (keeps its `version` field)
  - `application/LayoutService.java`, `port/in/SaveLayout.java`, `port/out/LayoutStore.java`
  - `adapter/in/web/LayoutController.java` and `LayoutUpdate`
  - `adapter/out/persistence/`

  Cross-module reads go through `port/out/MicroserviceIds` and `port/out/CardIds`, whose adapters in `adapter/out/` call `MicroserviceApi` and `SpecCardApi`. Update `T/it/LayoutAndCanvasIT.java` imports of `BaselineLayout`/`BaselineLayoutRepository` to the new packages.
- [ ] T038 🔖 [US2] Commit "005: workspace and layout — ports and adapters".

### card (domain split)

- [ ] T039 [US2] Split card into domain and persistence (data-model.md card):
  - Create the plain `B/card/domain/SpecCard.java` with all state and the behaviour `applyParsed`, `markSynced` and `markFailed`. It has no JPA or Jackson annotations.
  - Move today's entity to `B/card/adapter/out/persistence/SpecCardEntity.java`. Its columns and `@JdbcTypeCode(JSON)` fields stay identical, and Hibernate `validate` must pass.
  - Add `SpecCardJpaRepository` and `SpecCardMapper`.
  - `SpecCardStoreAdapter` does find → copy onto the managed entity → save (R7).
  - Widen package-private members only inside internal packages (visibility rule).
  - Update `T/it/*` setup that uses `SpecCard`/`SpecCardRepository` to `SpecCardEntity`/`SpecCardJpaRepository` (assertions unchanged).
- [ ] T040 [US2] Restructure card's application and adapter layers:
  - `B/card/application/SpecCardService.java` implements `port/in` (`ImportCard`, `SyncCard`, `DeleteCard`, `ListCards`) and `SpecCardApi`.
  - It uses `port/out/SpecCardStore`, `port/out/SpecSource` (adapter `adapter/out/fetch/HttpSpecFetcher`) and `port/out/SpecParserPort` (adapter `adapter/out/parser/SwaggerSpecParser`, which wraps parse and flatten).
  - The controller and request DTOs go to `adapter/in/web/`, with DTO names unchanged.
- [ ] T041 [US2] Move `B/microservice/CardLinkAdapter.java` → `B/microservice/adapter/out/card/CardLinkAdapter.java`. Add a `CardCatalogAdapter` that implements microservice's `port/out/CardCatalog` through `SpecCardApi.exists`. This is done here, ahead of T045, so the card commit compiles.
- [ ] T042 [P] [US2] Add `T/card/domain/SpecCardDomainTest.java`. It covers `applyParsed`, `markSynced`/`markFailed` state and the import duplicate rules through `SpecCardService` with in-memory fakes of `SpecCardStore`, `SpecSource`, `SpecParserPort` and `CardLinkPort`. No Spring context (SC-005).
- [ ] T043 🔖 [US2] Commit "005: card module — domain split, ports and adapters".

### microservice (domain split)

- [ ] T044 [US2] Split microservice into domain and persistence (data-model.md microservice):
  - Create the plain `B/microservice/domain/Microservice.java` with `rename`, `describe`, `own` and `link`, and move `Ownership` validation and the name and description checks with `shared.Names` into the domain.
  - Add `adapter/out/persistence/MicroserviceEntity.java`, `MicroserviceJpaRepository` and `MicroserviceMapper`.
  - `MicroserviceStoreAdapter` exposes `save`, `saveAndFlush`, `delete`, `findById`, `findByNameNormalized`, `findBySpecCardId` and `findAllOrdered`.
  - Widen package-private members only inside internal packages (visibility rule).
  - Update `T/it/*` setup that uses `Microservice`/`MicroserviceRepository` to `MicroserviceEntity`/`MicroserviceJpaRepository`, or to `MicroserviceApi` (assertions unchanged).
- [ ] T045 [US2] Restructure microservice's application and adapter layers:
  - `B/microservice/application/MicroserviceService.java` implements `port/in` (`CreateMicroservice`, `UpdateMicroservice`, `DeleteMicroservice`, `LinkCard`, `UnlinkCard`) and `MicroserviceApi`.
  - Its outbound ports are `port/out/MicroserviceStore`, `CardCatalog`, `Revision` (adapter to `WorkspaceRevision`) and `spi/ConnectionEndsPort`.
  - The controller and DTOs go to `adapter/in/web/`.
  - Update `CardLinkAdapter` to use `MicroserviceStore`.
  - Keep the ordering table in data-model.md for delete and rename.
- [ ] T046 [P] [US2] Add `T/microservice/domain/MicroserviceDomainTest.java`. It covers the name rules, duplicate names, ownership validation, and the delete order (detach before delete, then bump), checked with in-memory fakes of `MicroserviceStore`, `ConnectionEndsPort`, `CardCatalog` and `Revision`.
- [ ] T047 🔖 [US2] Commit "005: microservice module — domain split, ports and adapters".

### connection (domain split, incl. connections document)

- [ ] T048 [US2] Split connection into domain and persistence (data-model.md connection):
  - Create the plain `B/connection/domain/Connection.java` and `Flow.java` with their behaviour: `setSource`, `setTarget`, `isDangling`, and the step rules from `ConnectionSteps`.
  - Move the Jackson-free document logic into `B/connection/domain/document/`: `ConnectionsDocumentModel`, `ConnectionsDocumentDiff` and `ConnectionsDocumentChanges`. Move the JSON-handling classes (`ConnectionsDocumentReader`, `ConnectionsDocumentWriter`, `JsonLocator`) to `B/connection/adapter/out/document/` behind `B/connection/application/port/out/DocumentCodec.java` (`read(text) → model or DocumentErrors`, `write(model) → text`). The domain must not import Jackson (research R8, T056). Error locations and messages must stay identical.
  - Widen package-private members only inside internal packages (visibility rule).
  - Update `T/it/*` setup that uses `Connection`/`ConnectionRepository` to `ConnectionEntity`/`ConnectionJpaRepository` (assertions unchanged).
  - Add `adapter/out/persistence/ConnectionEntity.java`, `FlowEntity.java`, the JPA repositories (keep `findDanglingByName` and `findBySourceIdOrTargetId`), the mappers and `ConnectionStore`/`FlowStore` adapters with explicit `flush()`.
- [ ] T049 [US2] Restructure connection's application and adapter layers:
  - `B/connection/application/`: `ConnectionService`, `FlowService` and `ConnectionsDocumentService` implement `port/in` and `ConnectionApi`/`FlowApi`.
  - `port/out/MicroserviceDirectory` has an adapter at `adapter/out/microservice/MicroserviceDirectoryAdapter.java` that calls `MicroserviceApi`.
  - Move `ConnectionEndsAdapter` → `adapter/out/microservice/` and make it use `ConnectionStore`.
  - The controllers (`ConnectionController`, `FlowController`, `ConnectionsDocumentController`), `ConnectionsDocumentApi` and the request DTOs go to `adapter/in/web/`.
- [ ] T050 [P] [US2] Add the domain tests:
  - Move `ConnectionsDocumentDiffTest` to `T/connection/domain/document/` and `ConnectionsDocumentValidatorTest`, `ConnectionsDocumentWriterTest` and `JsonLocatorTest` to `T/connection/adapter/out/document/`, with unchanged assertions.
  - Add `T/connection/domain/ConnectionDomainTest.java` covering dangling ends, reattach by name and the step rules with in-memory store fakes.
- [ ] T051 🔖 [US2] Commit "005: connection module — domain split, ports and adapters".

### scenario (domain split) and canvas tidy-up

- [ ] T052 [US2] Restructure scenario (data-model.md scenario):
  - `B/scenario/domain/`: the `model/*` records, `ScenarioValidator` and a plain `Scenario`.
  - `adapter/out/persistence/SavedScenarioEntity.java` with the same JSON columns, plus a repository and a mapper.
  - The `model/*` records must carry no Jackson annotations; if any have them (for JSON column mapping), move that mapping to the persistence adapter (T056).
  - Update any `T/it/*` setup that uses the scenario entity (assertions unchanged).
  - `application/ScenarioService.java` with `port/out/ScenarioStore` and `port/out/Baseline`, whose adapter `adapter/out/canvas/` calls `CanvasApi.index()`.
  - `adapter/in/web/ScenarioController.java` and the `dto/` package.
- [ ] T053 [P] [US2] Add `T/scenario/domain/ScenarioDomainTest.java`. It checks that `ScenarioValidator` reports orphans and drift against a hand-built `BaselineIndex`, and that saving never calls `Revision`. No Spring.
- [ ] T054 [US2] Tidy up canvas:
  - `B/canvas/application/CanvasService.java` (implements `CanvasApi`)
  - `adapter/in/web/CanvasController.java` and `adapter/in/web/dto/*`, with names unchanged
  - Move `T/canvas/CanvasServiceTest.java` with imports updated.
- [ ] T055 🔖 [US2] Commit "005: scenario and canvas — ports and adapters".

### Enforce the layout

- [ ] T056 [US2] In `T/ModularityTest.java`:
  - Add `JMoleculesArchitectureRules.ensureHexagonal()` over the main classes.
  - Add an ArchUnit rule: classes in `..card.domain..`, `..microservice.domain..`, `..connection.domain..` and `..scenario.domain..` must not depend on `jakarta.persistence..`, `org.springframework.web..`, `org.springframework.data..` or `com.fasterxml.jackson..` (FR-015, research R8).
  - Fix any violations found.
- [ ] T057 🔖 [US2] Commit "005: enforce hexagonal layout rules".

**Checkpoint**: All 9 modules follow one layout, and the 4 core modules have infrastructure-free domain tests.

---

## Phase 5: User Story 3 — Living module documentation (P3)

**Goal**: Module documentation is generated from the code (FR-019, R10).

**Independent Test**: Follow quickstart §5. `docs/modules/` contains an overview diagram and one view per module, and they match contracts/module-apis.md.

- [ ] T058 [US3] Add a `writesDocumentation()` test to `T/ModularityTest.java`. It runs `new Documenter(modules, Documenter.Options.defaults().withOutputFolder("../docs/modules")).writeDocumentation()`, with the output folder relative to `backend/`. Check that it creates `components.puml` and the per-module `.puml` and `.adoc` files.
- [ ] T059 [US3] Write `docs/modules/README.md`: how to regenerate (`./mvnw test -Dtest=ModularityTest`), the module table, the two SPI inversions (`ConnectionEndsPort`, `CardLinkPort`) and why they are synchronous (R4, R5). Add a link to it from the root `README.md`.
- [ ] T060 🔖 [US3] Commit "005: generated module documentation".

---

## Phase 6: Polish & Cross-Cutting

- [ ] T061 Run the frontend e2e suite (quickstart §6): `npx playwright test` in `frontend/`. Confirm `git diff --stat -- frontend/` is empty since the baseline commit (SC-002, FR-005).
- [ ] T062 Run the live-data smoke check (quickstart §7): `docker compose up -d --build`, then compare `GET /api/canvas` and `GET /api/connections-document` with the T002 snapshots. Confirm Flyway reports no pending migration (FR-003).
- [ ] T063 Compare the `PerformanceSmokeIT` timings with `specs/005-modular-backend/baseline.md`, record them there, and confirm they are no more than 10% slower (SC-006).
- [ ] T064 [P] Update the project memory note and `README.md` (backend section) with the module structure and the `ModularityTest` gate.
- [ ] T065 🔖 Commit "005: verification and docs".

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → US1 → US2 → US3 → Polish**, strictly in this order. This is a refactor of one codebase: every phase moves classes that the next phase builds on, and each 🔖 commit must be green.
- **US1** depends on Phase 2: `shared` and `card.model` must exist before the boundaries can be declared.
- **US2** depends on US1: the module APIs and SPIs must exist before modules are split internally. The module order inside US2 is fixed: note → workspace/layout → card → microservice → connection → scenario/canvas. The lower modules come first so the upper modules' outbound adapters compile against finished APIs.
- **US3** technically only needs US1 (verify passes). It is scheduled after US2 so the committed diagrams show the final structure.
- **Ordering hot spots**: T013/T014, T017/T018 and T044/T045 must preserve data-model.md "Behaviours whose ordering must be preserved". The guards are `MicroserviceLinkIT`, `ConnectionIT` and `SpecCardIT`.

## Parallel Opportunities

- Phase 2: T010 (moving tests) runs alongside T009 once the file moves are clear.
- US1: T021 (NoteApi) and T027 (the package-info files) are separate files. The two cycle-breaks (T012–T015 and T016–T020) touch disjoint files, except `MicroserviceService`, so do them one after the other.
- US2: T034, T042, T046, T050 and T053 (tests) can be written in parallel with their module's restructuring. T036 (workspace) and T037 (layout) are independent.
- Polish: T061, T062 and T063 are independent checks.

```text
# Example: inside US2 after T039/T040 land
T042 SpecCardDomainTest      ║  T041 CardLinkAdapter move
# Example: workspace and layout together
T036 workspace restructure   ║  T037 layout restructure
```

## Implementation Strategy

1. **MVP = Phase 1 + Phase 2 + US1 (T001–T032).** This alone gives enforced, acyclic modules and unchanged behaviour. It is a safe place to stop and review.
2. **Incremental US2.** Convert one module per commit, starting with note as the template. Review the note commit before applying the pattern to the others.
3. **US3 and Polish** close the feature: docs, e2e, live data, performance and the constitution.

## Baseline violations

_Filled by T006 (before) and T031 (after, including the deliberate-violation check)._

- Before:
- After:
