---

description: "Task list for the Microservice Architecture & Scenario Visualizer"
---

# Tasks: Microservice Architecture & Scenario Visualizer

**Input**: Design documents from `/specs/002-architecture-visualizer/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Included. The constitution (Principle V and the Development Workflow & Quality Gates
section) requires JUnit 5 coverage of multi-spec parsing and edge inference, fixture-based
inference tests, and tests proving that verified edges and baseline data survive re-upload and
scenario edits. Test tasks are listed before the implementation tasks of each story. Write them
first and make sure they fail before implementing.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Web app: `backend/` (Maven, Spring Boot) and `frontend/` (npm, Vite), per [plan.md](./plan.md#project-structure)
- Backend Java root: `backend/src/main/java/com/refinements/visualizer/`, abbreviated **`BE/`** below
- Backend test root: `backend/src/test/java/com/refinements/visualizer/`, abbreviated **`BT/`** below
- Backend resources: `backend/src/main/resources/` and `backend/src/test/resources/`
- Frontend: `frontend/src/` and `frontend/tests/`
- Research decisions are cited as `R#` ([research.md](./research.md)). Field constraints are
  cited as `data-model.md Entity.field` ([data-model.md](./data-model.md)). API shapes are in
  [contracts/openapi.yaml](./contracts/openapi.yaml).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create the repository layout from plan.md (`backend/`, `frontend/`) with a root `.gitignore` covering Maven `target/`, `node_modules/`, `dist/`, the H2 `data/` folder and IDE files, in `.gitignore`
- [X] T002 Initialize the backend Maven project with the Maven wrapper in `backend/pom.xml`. Use the Spring Boot 3.5.x parent and Java 21, with dependencies spring-boot-starter-web, -validation, -data-jpa, -actuator, flyway-core, flyway-database-postgresql, postgresql, h2, `io.swagger.parser.v3:swagger-parser` 2.1.x and springdoc-openapi-starter-webmvc-ui 2.8.x. Test dependencies: spring-boot-starter-test, spring-boot-testcontainers, testcontainers postgresql and junit-jupiter. Add Spotless (palantir-java-format) bound to `verify` and Failsafe for `*IT` classes (versions per R17)
- [X] T003 [P] Initialize the frontend with Vite (React + TypeScript) in `frontend/package.json`. Dependencies: react@18.3, react-dom@18.3, @xyflow/react@12, elkjs@0.10, @tanstack/react-query@5, zustand@5, html-to-image@1.11 (R17). Dev dependencies: typescript@5, vitest, @testing-library/react, @testing-library/user-event, jsdom, @playwright/test, openapi-typescript@7, eslint, prettier. Scripts: `dev`, `build`, `typecheck` (`tsc --noEmit`), `test`, `e2e`, `gen:api`
- [X] T004 [P] Configure TypeScript `strict: true` (plus `noUncheckedIndexedAccess`) in `frontend/tsconfig.json`, and the Vite dev server with the `/api` → `http://localhost:8080` proxy and the elkjs web worker in `frontend/vite.config.ts`
- [X] T005 [P] Configure ESLint (typescript-eslint, react-hooks, ban `any` without an eslint-disable comment, per constitution V) and Prettier in `frontend/eslint.config.js` and `frontend/.prettierrc`
- [X] T006 [P] Configure Vitest (jsdom, React Testing Library setup file) in `frontend/vitest.config.ts` and `frontend/tests/setup.ts`, and Playwright (baseURL `http://localhost:5173`) in `frontend/playwright.config.ts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T007 Create `BE/VisualizerApplication.java` and the configuration files `backend/src/main/resources/application.yml`, `application-dev.yml` and `application-prod.yml`:
  - `application.yml`: `visualizer.inference.generic-fields` defaults (R5), `visualizer.inference.max-targets-per-field: 5`, `visualizer.inference.min-confidence: 0.4`, `visualizer.spec.max-bytes: 10485760`, `visualizer.http.max-body-bytes: 12582912` (JSON envelope and escaping overhead), `visualizer.fetch.connect-timeout: 5s`, `visualizer.fetch.read-timeout: 15s`, `visualizer.fetch.max-redirects: 3`, `visualizer.fetch.allowed-hosts: []` (empty means all), `visualizer.refs.remote-allowlist: []`, `spring.flyway.locations: classpath:db/migration/{vendor}`, and `spring.jpa.hibernate.ddl-auto: validate`
  - `application-dev.yml`: an H2 file database in PostgreSQL mode
  - `application-prod.yml`: PostgreSQL through environment variables
- [X] T008 [P] Create a typed `@ConfigurationProperties` record bound to the `visualizer.*` keys above in `BE/common/VisualizerProperties.java`
- [X] T009 [P] Create the domain exceptions in `BE/common/errors/`: `NotFoundException`, `ConflictException` (with an optional `conflict` code and `existingServiceKey`), `SpecInvalidException` (carrying `List<ParseError>`), `FetchFailedException`, `PayloadTooLargeException` and `StaleVersionException`. Put the `ParseError` record `{location, message}` in `BE/common/errors/ParseError.java`
- [X] T010 Implement the global RFC 9457 handler in `BE/common/ApiExceptionHandler.java`. It maps the exceptions to 404/409/422 (with `errors[]`)/502/413/409, Bean Validation failures to 400, and sets the request size limit, using the Problem schemas in contracts/openapi.yaml
- [X] T011 [P] Configure Jackson (ISO-8601 instants, omit nothing, fail on unknown properties for request DTOs) and a `Clock` bean in `BE/common/JacksonConfig.java` and `BE/common/TimeConfig.java`, plus `BE/common/RequestSizeLimitFilter.java`, which rejects request bodies over `visualizer.http.max-body-bytes` with 413 problem details
- [X] T012 [P] Configure springdoc (title and version matching contracts/openapi.yaml, `/api` group, Swagger UI at `/swagger-ui.html`) in `BE/common/OpenApiConfig.java`
- [X] T013 [P] Implement natural-key derivation (R6) in `BE/ingestion/ServiceKeys.java`:
  - `serviceKey(title, firstServerUrl)` for data-model.md `ServiceSpec.serviceKey`: slug plus optional `@host`, max 200 characters;
  - `nextFreeKey(base, existing)` for the `-2`, `-3` suffixes;
  - `endpointKey(serviceKey, method, path)` for data-model.md `ParsedEndpoint.endpointKey`, with path templates normalized to `{}`;
  - `edgeKey(...)` for data-model.md `EdgeOverride.edgeKey`, covering both the endpoint-level and the service-level form, with no length cap;
  - `edgeKeyHash(edgeKey)` for data-model.md `EdgeOverride.edgeKeyHash` (SHA-256, lower-case hex)
- [X] T014 [P] Unit-test key derivation in `BT/ingestion/ServiceKeysTest.java`: title slugging, the host suffix, suffix allocation, path-parameter renames producing the same endpoint key, the edge-key format, and a stable 64-character edge-key hash for keys longer than 600 characters
- [X] T015 [P] Implement field-name normalization (lower-case; strip `_`, `-` and spaces) for data-model.md `Field.normalizedName` (R4) in `BE/inference/FieldNormalizer.java`, with a unit test in `BT/inference/FieldNormalizerTest.java`
- [X] T016 [P] Implement the graph generation counter in `BE/graph/GraphGeneration.java`. It is an `AtomicLong` bean with `current()` and `bump()`, and writes to services, overrides and settings call `bump()` (R7)
- [X] T017 [P] Create the Testcontainers PostgreSQL 16 base class (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ServiceConnection`, profile `prod`, table cleanup between tests) in `BT/it/PostgresIntegrationTest.java`
- [X] T018 [P] Wire `npm run gen:api` to generate `frontend/src/api/schema.d.ts` from `specs/002-architecture-visualizer/contracts/openapi.yaml` with openapi-typescript (R9), run it once, and mark the file as generated in a header comment
- [X] T019 [P] Implement the typed fetch wrapper in `frontend/src/api/client.ts`. It uses `paths` from `schema.d.ts` and throws an `ApiError` with `status`, `title`, `detail`, `errors[]`, `conflict` and `existingServiceKey` parsed from Problem Details
- [X] T020 [P] Create the canvas UI store in `frontend/src/canvas/canvasStore.ts` (Zustand, R10). It holds the selected node, the highlight mode (`none|blast|search`), the search query and mode (`field|name`), expanded endpoint keys, the edge filter (`all|verified|inferred`), `displayMinConfidence` (a display-only filter, distinct from data-model.md `InferenceSettings.minConfidence`), and `presentationMode`
- [X] T021 Create the app shell in `frontend/src/main.tsx`, `frontend/src/App.tsx` and `frontend/src/styles/app.css`: QueryClientProvider, ReactFlowProvider, a top toolbar area, a central canvas area, a right side panel area and a toast region. It renders an empty canvas placeholder until US1

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Register services and see them on the canvas (Priority: P1) 🎯 MVP

**Goal**: Register services by pasting a spec or giving a URL, see each as a node with method badges, endpoints and expandable fields, re-sync, remove, and keep or auto-arrange the layout.

**Independent Test**: Register a Swagger 2.0 YAML spec by paste and an OpenAPI 3.x JSON spec by URL. Both nodes show the correct badges, endpoints and fully expanded fields. A re-sync after changing the URL's spec updates the node, and dragged positions survive a reload (quickstart V1).

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T022 [P] [US1] Add the spec fixtures:
  - In `backend/src/test/resources/specs/`, add the folders `swagger2/` (YAML and JSON), `oas30/`, `oas31/`, `refs/` (multi-level `$ref`, `allOf`, `oneOf`, recursive schema), `refs/multi-file/` (a root spec plus `schemas/order.yaml` and `schemas/common.json`, linked by relative refs), `refs/cross-host/` (a ref to a different host) and `invalid/` (missing ref, not a spec, empty `paths`).
  - Add `reference-workspace/` with the six files listed in quickstart.md, plus `expected-edges.json` listing the true connections.
  - Add a generated `large-100-endpoints.yaml`.
- [X] T023 [P] [US1] Write the parser tests in `BT/ingestion/SpecParserTest.java`:
  - Swagger 2.0 YAML and JSON both yield OpenAPI 3 models with the right data-model.md `ServiceSpec.specFormat`;
  - OAS 3.0 and 3.1 parse;
  - `$ref` chains are fully inlined;
  - the `refs/multi-file/` spec, served by an in-test `com.sun.net.httpserver.HttpServer` and parsed with its URL as the base location, **resolves fully** (constitution V, cross-file `$ref`);
  - a relative ref in a *pasted* spec, a ref to another host that is not allowlisted, and a missing ref each produce `ParseError`s with a `location` and reject the spec;
  - non-spec text is rejected;
  - the same input always gives the same output
- [X] T024 [P] [US1] Write the flattening tests in `BT/ingestion/FieldFlattenerTest.java`:
  - path, query and header parameters and body properties become inputs, and cookie parameters are skipped;
  - outputs come from the lowest 2xx response;
  - media types are preferred in R3 order;
  - array items get the `[]` path suffix;
  - `allOf` members are merged and `oneOf` branches tagged with `variant`;
  - recursion stops with `truncated=true` at depth 6;
  - endpoints are sorted by path, then method (data-model.md `ServiceSpec.endpoints`)
- [X] T025 [P] [US1] Write the registration integration tests in `BT/it/ServiceRegistrationIT.java` (extends PostgresIntegrationTest; URL specs are served by an in-test `com.sun.net.httpserver.HttpServer`):
  - `POST /api/services` with PASTE returns 201, and with URL returns 201;
  - registering an existing service with no `mode` returns 409 `DUPLICATE_SERVICE`; `mode=UPDATE` returns 200 with no duplicate, and `mode=NEW` gets a `-2` key;
  - identical content returns `unchanged=true`;
  - an invalid spec returns 422 with `errors[]` and creates no row;
  - content over 10 MB returns 413, while 9.9 MB of content (whose JSON body exceeds 10 MB after escaping) is accepted, and a body over 12 MB is rejected by the filter with 413;
  - `POST /api/services/{key}/sync` picks up changes, and a failed sync keeps the last good `rawContent` and `endpoints` with `lastSyncStatus=FAILED` and the errors stored;
  - `DELETE` returns 204;
  - URL schemes other than http(s) return 400
- [X] T026 [P] [US1] Write the layout and graph integration tests in `BT/it/LayoutAndGraphIT.java`:
  - `GET /api/graph` returns React Flow nodes (`type: service`, `position: null` when never placed) and `layoutVersion`;
  - `PUT /api/layout` round-trips positions and increments `version`;
  - a stale `version` returns 409;
  - positions of deleted services are pruned
- [X] T027 [P] [US1] Write the node rendering tests in `frontend/tests/canvas/CustomServiceNode.test.tsx`:
  - each zoom tier (< 0.45, 0.45–0.9, ≥ 0.9) renders the right level of detail (R12);
  - method badges show both a color class and a text label;
  - expanding an endpoint lists inputs and outputs with name, type and nesting;
  - more than 25 endpoints gives a scrollable list with "Show all";
  - a service with no endpoints shows the empty indicator;
  - a failed last sync shows the "sync failed" badge
- [X] T028 [P] [US1] Write the layout tests in `frontend/tests/canvas/elkLayout.test.ts`:
  - when no node has a position, the full ELK layout is applied;
  - when some do, new nodes go in a column right of the bounding box and existing positions are untouched;
  - `autoArrange()` returns new positions for every node and never saves by itself

### Implementation for User Story 1

- [X] T029 [P] [US1] Write the Flyway migration `V1__service_spec_and_layout.sql` in both `backend/src/main/resources/db/migration/postgresql/` (`jsonb`) and `.../h2/` (`JSON`):
  - table `service_spec` with the columns and constraints of data-model.md `ServiceSpec.id` (UUID PK), `ServiceSpec.serviceKey` (unique), `ServiceSpec.displayName`, `ServiceSpec.apiVersion`, `ServiceSpec.specFormat`, `ServiceSpec.sourceType`, `ServiceSpec.sourceUrl`, `ServiceSpec.rawContent`, `ServiceSpec.contentHash`, `ServiceSpec.endpoints`, `ServiceSpec.lastSyncAt`, `ServiceSpec.lastSyncStatus`, `ServiceSpec.lastSyncErrors`, `ServiceSpec.createdAt` and `ServiceSpec.updatedAt`;
  - table `baseline_layout` with data-model.md `BaselineLayout.id` (always 1, seeded), `BaselineLayout.positions` (seeded `{}`), `BaselineLayout.version` and `BaselineLayout.updatedAt`
- [X] T030 [P] [US1] Create the parsed-model records in `BE/ingestion/model/`:
  - `ParsedEndpoint.java` covering data-model.md `ParsedEndpoint.endpointKey`, `ParsedEndpoint.method`, `ParsedEndpoint.path`, `ParsedEndpoint.operationId`, `ParsedEndpoint.summary`, `ParsedEndpoint.inputs` and `ParsedEndpoint.outputs`;
  - `Field.java` covering data-model.md `Field.path`, `Field.name`, `Field.normalizedName`, `Field.type` (`unknown` fallback), `Field.format`, `Field.location`, `Field.required`, `Field.variant` and `Field.truncated`;
  - the enums `HttpMethod.java`, `FieldLocation.java` and `SpecFormat.java`
- [X] T031 [US1] Create the JPA entity `BE/service/ServiceSpec.java` and `BE/service/ServiceSpecRepository.java` (`findByServiceKey`, `existsByServiceKey`, `findAllByOrderByDisplayNameAsc`). Map data-model.md `ServiceSpec.endpoints` and `ServiceSpec.lastSyncErrors` with `@JdbcTypeCode(SqlTypes.JSON)` (R8), and add enum columns for `ServiceSpec.sourceType` and `ServiceSpec.lastSyncStatus` plus audit timestamps
- [X] T032 [P] [US1] Create the JPA entity `BE/layout/BaselineLayout.java` (JSON column for data-model.md `BaselineLayout.positions`, `@Version` on `BaselineLayout.version`) and `BE/layout/BaselineLayoutRepository.java`
- [X] T033 [US1] Implement the URL fetcher (R2) in `BE/ingestion/SpecFetcher.java`. It uses `RestClient` over the JDK HttpClient; only http(s) schemes (data-model.md `ServiceSpec.sourceUrl`); connect and read timeouts and the redirect cap from `VisualizerProperties`; a streaming read capped at `visualizer.spec.max-bytes`, which throws `PayloadTooLargeException`; the optional `allowed-hosts` check; and no credentials. Failures throw `FetchFailedException` with a human-readable reason (unreachable, 401/403 "requires credentials", timeout)
- [X] T034 [US1] Implement the parser wrapper (R1) in `BE/ingestion/SpecParser.java`:
  - call `OpenAPIParser().readContents` with `resolve`, `resolveFully` and `resolveCombinators=false`;
  - for `sourceType=URL`, pass the source URL as the base location so relative refs resolve, with every ref fetch going through `SpecFetcher`. Same-host refs are allowed by default; refs to other hosts are blocked unless they are on `visualizer.refs.remote-allowlist`. Pasted specs get no base location, so their relative refs are reported as unresolved with a "register by URL" hint. Check the pinned version's `readContents` location overload and `ParseOptions` setter names, as R1 flags;
  - detect data-model.md `ServiceSpec.specFormat` from the `swagger`/`openapi` root key;
  - map `getMessages()` to `ParseError(location, message)`;
  - throw `SpecInvalidException` when the model is null or any message mentions an unresolved reference
- [X] T035 [US1] Implement schema flattening (R3) in `BE/ingestion/FieldFlattener.java`. It builds `List<ParsedEndpoint>` from an `OpenAPI` model, using `ServiceKeys.endpointKey` and `FieldNormalizer` for data-model.md `Field.normalizedName`, and enforces the cycle and depth rule that sets data-model.md `Field.truncated`
- [X] T036 [US1] Implement `BE/service/ServiceRegistrationService.java`:
  - `register(request)`: fetch or take the content, enforce the 10 MB limit (data-model.md `ServiceSpec.rawContent`) on the UTF-8 bytes of the decoded `content` or fetched body, not on the HTTP request size, parse, flatten, derive the key, and resolve clashes per the `mode` rules in data-model.md §1 Lifecycle;
  - `sync(serviceKey, optionalContent)`: a no-op when the SHA-256 data-model.md `ServiceSpec.contentHash` matches. On failure, keep the last good `ServiceSpec.rawContent` and `ServiceSpec.endpoints`, and set `ServiceSpec.lastSyncStatus=FAILED`, `ServiceSpec.lastSyncErrors` and `ServiceSpec.lastSyncAt`;
  - `delete(serviceKey)`;
  - every successful write calls `GraphGeneration.bump()`
- [X] T037 [US1] Create the request and response DTO records `BE/service/dto/RegisterServiceRequest.java` (Bean Validation: PASTE needs `content`, URL needs a valid http(s) `url`; `mode` is optional) and `BE/service/dto/ServiceSummary.java` (including `endpointCount` and `unchanged`), matching contracts/openapi.yaml
- [X] T038 [US1] Implement `BE/service/ServiceController.java` with `GET /api/services`, `POST /api/services` (201 when new, 200 when updated or unchanged), `DELETE /api/services/{serviceKey}` and `POST /api/services/{serviceKey}/sync`
- [X] T039 [P] [US1] Create the React Flow DTO records in `BE/graph/dto/`, matching contracts/openapi.yaml exactly: `GraphResponse.java`, `GraphNode.java`, `ServiceNodeData.java` (including the traceability fields `specFormat`, `sourceUrl`, `contentHash` and `lastSyncAt`), `EndpointDto.java`, `FieldDto.java`, `XYPosition.java`, `GraphEdge.java`, `EdgeData.java` and `FieldMatchDto.java`
- [X] T040 [US1] Implement `BE/graph/GraphService.java` and `BE/graph/GraphController.java` (`GET /api/graph`). The US1 version builds nodes from all `ServiceSpec` rows plus positions from `BaselineLayout`, using `null` for unplaced nodes, and fills the node traceability fields from data-model.md `ServiceSpec.specFormat`, `ServiceSpec.sourceUrl`, `ServiceSpec.contentHash` and `ServiceSpec.lastSyncAt` (data-model.md §1 Traceability). It returns an empty `edges` list and `layoutVersion`, caches the response by `GraphGeneration.current()`, and adds `generation` to the response
- [X] T041 [US1] Implement `BE/layout/LayoutController.java` (`PUT /api/layout`). It replaces data-model.md `BaselineLayout.positions`, prunes keys with no `ServiceSpec`, and checks data-model.md `BaselineLayout.version` (a stale version throws `StaleVersionException`, giving 409)
- [X] T042 [P] [US1] Add the TanStack Query hooks for US1 in `frontend/src/api/queries.ts`: `useGraph`, `useServices`, `useRegisterService`, `useSyncService`, `useDeleteService` and `useSaveLayout`. Each mutation invalidates `graph`
- [X] T043 [P] [US1] Create `frontend/src/canvas/nodes/MethodBadge.tsx` and its styles. Each method has a color plus a text label (GET green, POST blue, PUT orange, PATCH teal, DELETE red, others grey) per R13
- [X] T044 [US1] Implement `frontend/src/canvas/nodes/CustomServiceNode.tsx` (`React.memo`) per R12:
  - zoom tier from a `useStore` selector returning only the tier;
  - a header with the name, version and sync-failed badge;
  - endpoint rows with `MethodBadge`, the path and an expand chevron;
  - a field list with IN and OUT sections and nesting indentation;
  - `in:{endpointKey}`/`out:{endpointKey}` handles on each row, plus `in:service`/`out:service` handles;
  - `updateNodeInternals` after expand and collapse;
  - a scroll and "Show all" control above 25 endpoints, and the empty-endpoints indicator
- [X] T045 [US1] Implement `frontend/src/canvas/layout/elkLayout.ts` and `frontend/src/canvas/layout/elk.worker.ts` (R11): `layered`, direction RIGHT, measured node sizes, `autoArrange(nodes, edges)`, and `placeUnpositioned(nodes)` (first load runs ELK; later loads use a column to the right of the bounding box)
- [X] T046 [US1] Implement `frontend/src/canvas/ArchitectureCanvas.tsx` and mount it in `App.tsx`:
  - `ReactFlow` with `nodeTypes={{ service: CustomServiceNode }}`, Controls and MiniMap;
  - load `useGraph` and place unpositioned nodes (saving positions the first time);
  - send drags as a debounced (1 s) `PUT /api/layout` with `layoutVersion`; on 409, show a toast ("changed in another tab") and refetch;
  - an **Auto-arrange** button that previews the result and saves only after the user confirms
- [X] T047 [US1] Implement `frontend/src/ingestion/RegisterServiceDialog.tsx`:
  - Paste and URL tabs;
  - on submit, show the 422 `errors[]` as a list of `location: message`, and 413 and 502 messages;
  - on 409 `DUPLICATE_SERVICE`, ask "Update existing" or "Register as new" and resubmit with `mode`;
  - an "Add service" toolbar button
- [X] T048 [US1] Implement `frontend/src/ingestion/ResyncButton.tsx` and a node action menu in `CustomServiceNode.tsx`:
  - re-sync for URL services;
  - for pasted services, a re-sync dialog to paste new content;
  - a remove-service action with a confirmation;
  - toasts for "unchanged", success and failure (the last good version is kept)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently (quickstart V1)

---

## Phase 4: User Story 2 - Automatic and manual connections (Priority: P1)

**Goal**: Inferred dashed arrows from response fields to matching inputs in other services, ignoring generic fields, with explainable confidence. Users can confirm (solid), delete false positives (kept dismissed), restore, and draw manual connections. All overrides survive re-sync and are flagged when orphaned.

**Independent Test**: With the reference workspace, order → customer (`customerId`) appears dashed and no arrow comes from `id` or `status`. Confirming turns it solid; deleting another keeps it hidden after re-syncing every service; removing an endpoint flags a confirmed edge as orphaned (quickstart V2).

### Tests for User Story 2 ⚠️

- [X] T049 [P] [US2] Write the inference engine tests in `BT/inference/EdgeInferenceEngineTest.java`:
  - every row of the R4 scoring table, including exact vs. normalized-only matches, compatible vs. incompatible types, the entity-qualified bonus and the path/required bonus;
  - the 0.4 floor;
  - generic fields excluded before indexing;
  - no edges within one service;
  - one edge per endpoint pair, with `confidence = max` and all `matches` listed;
  - the fan-out cap of 5 with `hiddenCandidates`;
  - shuffled input giving identical sorted output (determinism)
- [X] T050 [P] [US2] Write `BT/inference/ReferencePrecisionTest.java`. It loads `reference-workspace/` and compares the result with `expected-edges.json`: precision must be at least 0.8 (SC-003), and no emitted edge may rely only on generic fields
- [X] T051 [P] [US2] Write the graph merge tests in `BT/graph/GraphServiceTest.java` (R7):
  - a DISMISSED edge is removed;
  - a CONFIRMED edge becomes `origin: verified` with `overrideKind`;
  - a MANUAL edge is added with endpoint handles or `out:service`/`in:service` handles;
  - overrides that point at missing service or endpoint keys get `orphaned: true`;
  - the cache is invalidated when the generation is bumped
- [X] T052 [P] [US2] Write the override integration tests in `BT/it/EdgeOverrideIT.java`:
  - CONFIRM, DISMISS, RESTORE, CREATE_MANUAL and DELETE (a manual edge is soft-deleted, keeps its `deletedAt` and disappears from the graph and the override list; a confirmed edge becomes dismissed);
  - an override whose edge key is longer than 600 characters is stored and found again by hash;
  - CONFIRM or DISMISS on a key whose soft-deleted MANUAL row still exists updates that row (no unique-constraint error, one row remains);
  - CONFIRM or DISMISS on a key that is not inferred returns 409;
  - a self-connection returns 400;
  - CREATE_MANUAL on a key that is currently inferred is stored as CONFIRMED;
  - `GET /api/edges/overrides?kind=DISMISSED` lists dismissals;
  - **every override keeps its state after all services are re-synced (SC-004)**;
  - removing an endpoint through a re-sync flags the confirmed edge as orphaned and does not delete it
- [X] T053 [P] [US2] Write `BT/it/InferenceSettingsIT.java`:
  - `GET /api/settings/inference` returns the seeded defaults;
  - `PUT` stores names normalized and validates the ranges;
  - after adding `customerId` to the generic list, `GET /api/graph` no longer contains the order → customer edge
- [X] T054 [P] [US2] Write `frontend/tests/canvas/retargetEdges.test.ts`. When an endpoint row is collapsed or the zoom tier is low, the edge handles are re-targeted to `out:service`/`in:service`; otherwise they stay unchanged

### Implementation for User Story 2

- [X] T055 [P] [US2] Write the Flyway migration `V2__inference_and_overrides.sql` in both `db/migration/postgresql/` and `db/migration/h2/`:
  - table `edge_override` with data-model.md `EdgeOverride.id` (UUID PK), `EdgeOverride.edgeKey` (`text`, not null), `EdgeOverride.edgeKeyHash` (unique), `EdgeOverride.kind`, `EdgeOverride.sourceServiceKey`, `EdgeOverride.targetServiceKey` (a CHECK that it differs from the source), `EdgeOverride.sourceEndpointKey`, `EdgeOverride.targetEndpointKey` (a CHECK that both are null or both set), `EdgeOverride.snapshotMatches`, `EdgeOverride.note`, `EdgeOverride.deletedAt`, `EdgeOverride.createdAt` and `EdgeOverride.updatedAt`;
  - table `inference_settings` with data-model.md `InferenceSettings.id` (always 1), `InferenceSettings.genericFields`, `InferenceSettings.maxTargetsPerField` (CHECK 1–50), `InferenceSettings.minConfidence` (CHECK 0–1) and `InferenceSettings.updatedAt`
- [X] T056 [P] [US2] Create the JPA entity `BE/edge/EdgeOverride.java`, the enum `BE/edge/OverrideKind.java` for data-model.md `EdgeOverride.kind`, and `BE/edge/EdgeOverrideRepository.java` (`findByEdgeKeyHash`, `findAllByKindAndDeletedAtIsNull`). Set data-model.md `EdgeOverride.edgeKeyHash` from `ServiceKeys.edgeKeyHash` in a `@PrePersist`/`@PreUpdate` hook. Map data-model.md `EdgeOverride.snapshotMatches` as JSON
- [X] T057 [P] [US2] Create the JPA entity `BE/inference/InferenceSettings.java`, its repository, and `BE/inference/InferenceSettingsInitializer.java`. The initializer seeds the single row from `VisualizerProperties` on first start; data-model.md `InferenceSettings.genericFields` is stored normalized with `FieldNormalizer`
- [X] T058 [US2] Implement the R4 scoring table in `BE/inference/ConfidenceScorer.java`. Given a pair (output `Field`, input `Field`), it returns a score from 0 to 1 using data-model.md `Field.name`, `Field.normalizedName`, `Field.type`, `Field.format`, `Field.location` and `Field.required`
- [X] T059 [US2] Implement `BE/inference/EdgeInferenceEngine.java` (R4):
  - build the normalized-name → input index across services, excluding generic names;
  - match every output against the index, skipping inputs in the same service;
  - drop matches below `minConfidence`;
  - aggregate per endpoint pair into an `InferredEdge` (edge key from `ServiceKeys.edgeKey`);
  - apply the `maxTargetsPerField` cap with `hiddenCandidates`;
  - sort deterministically
- [X] T060 [US2] Extend `BE/graph/GraphService.java` with the R7 merge:
  - run the inferred edges, then remove DISMISSED, mark CONFIRMED as verified, add MANUAL overrides that are not soft-deleted, and compute `orphaned` against the current service and endpoint keys;
  - map to `GraphEdge`: `type` is `inferred` or `verified`, handles are `out:{sourceEndpointKey}`/`in:{targetEndpointKey}` or the service handles, and `data` carries `confidence`, `matches`, `hiddenCandidates` and `orphaned`;
  - keep the generation cache
- [X] T061 [US2] Implement `BE/edge/EdgeOverrideService.java` with the data-model.md §2 Rules and State transitions:
  - CONFIRM and DISMISS require the edge key to be currently inferred, and capture data-model.md `EdgeOverride.snapshotMatches`;
  - RESTORE deletes a DISMISSED override;
  - CREATE_MANUAL validates data-model.md `EdgeOverride.sourceServiceKey` ≠ `EdgeOverride.targetServiceKey` and that the services and endpoints exist, and is stored as CONFIRMED if the key is currently inferred;
  - DELETE soft-deletes a MANUAL override by setting data-model.md `EdgeOverride.deletedAt`, or turns a CONFIRMED one into DISMISSED. CREATE_MANUAL on a soft-deleted key clears `deletedAt`. Validates data-model.md `EdgeOverride.note` (max 500);
  - keeps one row per data-model.md `EdgeOverride.edgeKeyHash`: every transition looks the row up by hash and updates it in place (setting `kind`, clearing `deletedAt`), never inserting a duplicate;
  - every write calls `GraphGeneration.bump()`
- [X] T062 [US2] Implement `BE/edge/EdgeOverrideController.java` (`POST /api/edges/override`, `GET /api/edges/overrides?kind=`) and the DTOs `BE/edge/dto/EdgeOverrideRequest.java` and `BE/edge/dto/EdgeOverrideDto.java` (including the computed `orphaned`), per contracts/openapi.yaml
- [X] T063 [US2] Implement `BE/inference/InferenceSettingsController.java` (`GET` and `PUT /api/settings/inference`). It validates data-model.md `InferenceSettings.maxTargetsPerField` and `InferenceSettings.minConfidence` ranges, normalizes `InferenceSettings.genericFields`, and calls `GraphGeneration.bump()`
- [X] T064 [US2] Add the US2 hooks to `frontend/src/api/queries.ts`: `useOverrideEdge`, `useOverrides(kind)`, `useInferenceSettings` and `useUpdateInferenceSettings`. Each invalidates `graph` (and `overrides` where relevant)
- [X] T065 [US2] Implement the edge components `frontend/src/canvas/edges/InferredEdge.tsx` (dashed grey, arrow marker, confidence label on hover or selection) and `frontend/src/canvas/edges/VerifiedEdge.tsx` (solid dark, arrow marker; amber with a ⚠ marker and a "needs review" tooltip when `orphaned`) per R13, and register `edgeTypes` in `ArchitectureCanvas.tsx`
- [X] T066 [US2] Implement handle re-targeting as a pure function in `frontend/src/canvas/edges/retargetEdges.ts`. It takes edges, the zoom tier and the expanded-endpoint set from `canvasStore` and returns edges whose handles are all rendered (R12), and is applied in `ArchitectureCanvas.tsx`
- [X] T067 [US2] Implement `frontend/src/edges/EdgeDetailsPanel.tsx`, opened when an edge is clicked. It shows the matched field pairs with locations and scores, the confidence, the `hiddenCandidates` note and the orphan review note, plus **Confirm**/**Delete** for inferred edges and **Delete** for verified ones
- [X] T068 [US2] Add manual connections in `frontend/src/canvas/ArchitectureCanvas.tsx`. `onConnect` sends `CREATE_MANUAL` with endpoint keys parsed from the handle IDs (or service level), and `isValidConnection` rejects source == target
- [X] T069 [P] [US2] Implement `frontend/src/edges/DismissedEdgesList.tsx`: a side-panel list from `useOverrides('DISMISSED')` with **Restore** (RESTORE action), opened from the toolbar
- [X] T070 [US2] Implement `frontend/src/canvas/EdgeFilterBar.tsx` (all, verified or inferred, and a `displayMinConfidence` slider stored in `canvasStore`, which only hides edges client-side and never changes `InferenceSettings.minConfidence`), and apply the filter to the rendered edges in `ArchitectureCanvas.tsx`
- [X] T071 [P] [US2] Implement `frontend/src/settings/InferenceSettingsDialog.tsx`: edit the generic field list (chips), `maxTargetsPerField` and `minConfidence`, and show API validation errors

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently (quickstart V2)

---

## Phase 5: User Story 3 - Blast radius and field search (Priority: P2)

**Goal**: Clicking a service highlights its direct upstream producers (blue) and downstream consumers (purple) and dims the rest. Field search highlights every service producing or consuming a field, and name search jumps to services and endpoints.

**Independent Test**: For A → B → C plus D, clicking B highlights A and C and dims D (and E in A → B → C → E). Searching `customerid` outlines both the producer (OUT) and the consumer (IN). Esc clears the highlight (quickstart V3).

### Tests for User Story 3 ⚠️

- [X] T072 [P] [US3] Write `frontend/tests/canvas/blastRadius.test.ts`:
  - only one hop is highlighted;
  - a bidirectional A ↔ B pair puts B in both sets with no duplicates;
  - a cycle terminates;
  - the edge filter (verified only or inferred only) is respected;
  - upstream and downstream counts are correct;
  - proposed overlay edges are included when passed in (used by US4)
- [X] T073 [P] [US3] Write `frontend/tests/canvas/fieldSearch.test.ts`:
  - matching is case-insensitive and normalized (`customer_id` matches `customerId`);
  - nested paths match (`order.customerId`);
  - each hit says IN or OUT and lists the matching endpoint keys and field paths;
  - no matches gives an empty result;
  - name-search mode matches service display names and endpoint paths

### Implementation for User Story 3

- [X] T074 [P] [US3] Implement the pure function `frontend/src/canvas/highlight/blastRadius.ts`: `(selectedId, edges, filter) → { upstream: Set, downstream: Set, edgeIds: Set }`, one hop, de-duplicated (R14)
- [X] T075 [P] [US3] Implement the pure function `frontend/src/canvas/search/fieldSearch.ts`: field mode works over `Field.normalizedName` and `Field.path` from the graph DTO, normalized the same way as `FieldNormalizer` (R14); name mode works over service names and endpoint paths
- [X] T076 [US3] Apply the highlight states in `frontend/src/canvas/nodes/CustomServiceNode.tsx`, `frontend/src/canvas/edges/InferredEdge.tsx` and `frontend/src/canvas/edges/VerifiedEdge.tsx`: upstream blue outline, downstream purple outline, 25% opacity when dimmed, search hits in a yellow outline with IN/OUT tags on matching field rows, and highlighted edges between the selection and its neighbours (R13)
- [X] T077 [US3] Wire the interactions in `frontend/src/canvas/ArchitectureCanvas.tsx`: `onNodeClick` sets the blast selection, `onPaneClick` and the Escape key clear it, and a new `frontend/src/canvas/HighlightSummary.tsx` chip shows the upstream and downstream counts
- [X] T078 [US3] Implement `frontend/src/canvas/search/SearchBar.tsx`. It has a toolbar input with a Field/Name toggle, a results list (service, endpoint, IN/OUT), and a "No matches" message; picking a result calls `fitView` on that node (FR-029)

**Checkpoint**: User Stories 1–3 work independently (quickstart V3)

---

## Phase 6: User Story 4 - Draft Scenario Mode (Priority: P2)

**Goal**: Toggle a draft overlay to add proposed services, endpoints and green dashed connections without touching the baseline. Save, reopen, rename and delete named scenarios, export them as JSON (re-importable) or as a PNG, and flag references to baseline items that no longer exist.

**Independent Test**: In draft mode, add a proposed service with one endpoint and connect it to an existing service with a green dashed arrow. After toggling off, `GET /api/graph` is byte-identical to before. The JSON export re-imports to the same scenario, and the PNG export shows the proposed styling (quickstart V4).

### Tests for User Story 4 ⚠️

- [X] T079 [P] [US4] Write `frontend/tests/scenario/overlay.test.ts`:
  - with a deep-frozen baseline `GraphResponse`, `applyOverlay(baseline, draft)` never mutates it;
  - proposed services render as `proposed: true` nodes;
  - `addedEndpoints` appear as proposed rows on baseline nodes;
  - proposed edges are `type: proposed`;
  - scenario positions override baseline positions;
  - an empty draft returns the baseline nodes and edges unchanged (SC-007)
- [X] T080 [P] [US4] Write `frontend/tests/scenario/scenarioStore.test.ts`:
  - edits set `dirty`;
  - `toScenarioWrite()` and `toScenarioDocument()` produce contract-shaped payloads (`formatVersion: 1`, `proposed:` temp keys, `version` carried for PUT);
  - loading a saved scenario clears `dirty`;
  - removing a proposed service cascades to its proposed edges
- [X] T081 [P] [US4] Write `BT/it/ScenarioIT.java`:
  - CRUD on `/api/scenarios`;
  - a case-insensitive duplicate name returns 409;
  - a stale `version` on PUT returns 409;
  - validation rejects a missing `proposed:` prefix and source == target;
  - `GET /{id}` returns `orphans` after an endpoint used by the scenario is removed through a re-sync, and `baselineDrift` after a spec changes;
  - `POST /import` suffixes names on a clash;
  - `GET /{id}/export` sets `Content-Disposition`;
  - **the `GET /api/graph` response is identical before and after any scenario create, update or delete (SC-007)**

### Implementation for User Story 4

- [X] T082 [P] [US4] Write the Flyway migration `V3__saved_scenario.sql` in both `db/migration/postgresql/` and `db/migration/h2/`: table `saved_scenario` with data-model.md `SavedScenario.id`, `SavedScenario.name` (max 120), `SavedScenario.nameNormalized` (max 120, a plain unique constraint in both vendor scripts), `SavedScenario.description` (max 2000), `SavedScenario.content`, `SavedScenario.baselineRef`, `SavedScenario.version`, `SavedScenario.createdAt` and `SavedScenario.updatedAt`
- [X] T083 [P] [US4] Create the JPA entity `BE/scenario/SavedScenario.java` (`@Version` on data-model.md `SavedScenario.version`, JSON columns for `SavedScenario.content` and `SavedScenario.baselineRef`), `BE/scenario/SavedScenarioRepository.java` (`existsByNameNormalized`, `findAllByOrderByUpdatedAtDesc`), with data-model.md `SavedScenario.nameNormalized` set to `lower(trim(name))` in a `@PrePersist`/`@PreUpdate` hook, and the content records in `BE/scenario/model/`:
  - `ScenarioContent.java` for data-model.md `ScenarioContent.proposedServices`, `ScenarioContent.addedEndpoints`, `ScenarioContent.proposedEdges` and `ScenarioContent.positions`;
  - `ProposedService.java`, `ProposedEndpoint.java`, `ProposedEdge.java` and `SimpleField.java`
- [X] T084 [US4] Implement `BE/scenario/ScenarioValidator.java` per the data-model.md §3 Rules:
  - the `proposed:` prefix on each `ProposedService.tempKey` in data-model.md `ScenarioContent.proposedServices`, with no clash against baseline keys;
  - source ≠ target for every edge in data-model.md `ScenarioContent.proposedEdges`;
  - additions only, so no baseline mutation fields;
  - the orphan check against the current `ServiceSpec` keys, producing `Orphan{ref, usedBy}`;
  - `baselineDrift`, comparing data-model.md `SavedScenario.baselineRef` with the current `ServiceSpec.contentHash`;
  - import validation that `formatVersion == 1`
- [X] T085 [US4] Implement `BE/scenario/ScenarioService.java` and `BE/scenario/ScenarioController.java` (`GET/POST /api/scenarios`, `GET/PUT/DELETE /api/scenarios/{id}`, `POST /api/scenarios/import`, `GET /api/scenarios/{id}/export`) with the DTOs in `BE/scenario/dto/` (`ScenarioWrite`, `ScenarioSummary`, `ScenarioDto` with `orphans`/`baselineDrift`, `ScenarioDocument`):
  - capture `baselineRef` on save;
  - enforce case-insensitive uniqueness of data-model.md `SavedScenario.name` through `SavedScenario.nameNormalized`, adding the "(imported)" suffix on import clashes;
  - check the version, which gives 409;
  - never touch `ServiceSpec`, `EdgeOverride` or `BaselineLayout`, and do not bump `GraphGeneration`
- [X] T086 [US4] Add the US4 hooks to `frontend/src/api/queries.ts`: `useScenarios`, `useScenario(id)`, `useCreateScenario`, `useUpdateScenario`, `useDeleteScenario` and `useImportScenario`
- [X] T087 [P] [US4] Implement `frontend/src/scenario/scenarioStore.ts` (Zustand, R10):
  - state: `isDraftMode`, `scenarioId`, `name`, `version`, `draft: ScenarioContent` and `dirty`;
  - actions: add/remove proposed service, add/remove endpoint (on a proposed or baseline service), add/remove proposed edge, set position, load, reset, `toScenarioWrite()` and `toScenarioDocument()`;
  - no persistence to `localStorage`
- [X] T088 [P] [US4] Implement the pure memoized selector `applyOverlay(baseline, draft)` in `frontend/src/scenario/overlay.ts`. It returns new node and edge arrays with `proposed` flags, merged `addedEndpoints` and scenario positions, and never mutates the baseline (Principle IV)
- [X] T089 [US4] Implement `frontend/src/canvas/edges/ProposedEdge.tsx` (green dashed with an arrow marker) and the proposed styling in `frontend/src/canvas/nodes/CustomServiceNode.tsx` (a dashed green border with a "PROPOSED" tag for proposed nodes, and a green "+" row for added endpoints) per R13, and register `proposed` in `edgeTypes`
- [X] T090 [US4] Implement `frontend/src/scenario/ScenarioToolbar.tsx`:
  - a Draft Scenario Mode toggle, and a green banner "Draft scenario: {name}" with a green canvas frame;
  - **Save** / **Save as** (PUT with `version`; on 409, show a "changed in another tab" dialog offering reload or save as a copy);
  - a confirmation when leaving draft mode with unsaved changes, and a `beforeunload` guard while `dirty`
- [X] T091 [US4] Implement `frontend/src/scenario/ProposedServiceForm.tsx`: add a proposed service (name and endpoints with method, path and optional fields), add a proposed endpoint to the selected baseline or proposed service, and remove proposals
- [X] T092 [US4] Integrate draft mode into `frontend/src/canvas/ArchitectureCanvas.tsx`:
  - render `applyOverlay(graph, draft)` while in draft mode;
  - `onConnect` creates proposed edges instead of manual overrides;
  - drags write to `scenarioStore` positions instead of `PUT /api/layout`;
  - baseline override actions (confirm, delete) are disabled;
  - blast radius and search run over the overlay graph (FR-037)
- [X] T093 [US4] Implement `frontend/src/scenario/ScenarioList.tsx`: a side panel listing saved scenarios with open, rename, delete and **Import JSON** (file picker → `useImportScenario`). Opening a scenario loads it into `scenarioStore`, enters draft mode, and shows the `orphans` and `baselineDrift` flags on the affected proposed edges and endpoints (FR-038)
- [X] T094 [P] [US4] Implement `frontend/src/scenario/export/exportJson.ts`. It downloads `toScenarioDocument()` as `<name>.scenario.json`, works for unsaved drafts, and uses `GET /api/scenarios/{id}/export` for saved scenarios
- [X] T095 [US4] Implement `frontend/src/scenario/export/exportImage.ts` (R15): `html-to-image` `toPng` of `.react-flow__viewport` framed with `getNodesBounds` and `getViewportForBounds`, at pixel ratio 2, including the legend and the draft banner. Add an **Export → JSON | Image** menu in `ScenarioToolbar.tsx`, with image export also available outside draft mode (FR-036)

### Follow-up (after review): proposed fields on existing endpoints

- [X] T105 [US4] Add `ScenarioContent.addedFields` (data-model.md `ScenarioContent.addedFields`, optional for older exports) and `AddedField` to contracts/openapi.yaml, `BE/scenario/model/AddedField.java`, `ScenarioValidator` (endpoint belongs to service, orphan check, drift) and `BT/it/ScenarioIT.java`
- [X] T106 [US4] Add `addField`/`removeField` to `frontend/src/scenario/scenarioStore.ts` (stored on proposed or added endpoints directly, otherwise in `addedFields`) and merge proposed fields in `frontend/src/scenario/overlay.ts`, with tests
- [X] T107 [US4] Add the "+ Add field" control and green proposed field rows (removable) to `frontend/src/canvas/nodes/CustomServiceNode.tsx`, and list proposed fields in `ProposedServiceForm.tsx`
- [X] T108 [US4] Nested proposed fields: a "+" on object/array field rows (baseline or proposed) adds a child field stored as a path (`shipping.city`, `items[].discount`); `mergeFields` in `frontend/src/scenario/overlay.ts` places children under their parent, and removing a field removes its descendants, with tests

**Checkpoint**: All user stories should now be independently functional (quickstart V4)

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T096 [P] Implement `frontend/src/canvas/Legend.tsx`, explaining the edge styles, proposed styling and highlight colors (R13) in the canvas corner. It is included in image exports
- [X] T097 [P] Implement presentation mode and full screen (FR-040) in `frontend/src/canvas/PresentationToggle.tsx` and `frontend/src/styles/presentation.css`: larger fonts, badges and stroke widths, and a Fullscreen API toggle, all tied to `canvasStore.presentationMode`
- [X] T098 [P] Add `BT/it/PerformanceSmokeIT.java`: seed 50 services (one with 100 endpoints, from `large-100-endpoints.yaml`) and assert that registration takes under 10 s (SC-002) and that `GET /api/graph` with about 500 edges is built in under 300 ms once warm (plan Performance Goals)
- [X] T099 [P] Add `frontend/tests/canvas/largeGraph.test.tsx`, rendering a synthetic 50-node, 500-edge graph and asserting that a selection change re-renders only the affected nodes (memoization check, SC-006)
- [X] T100 [P] Add `BT/it/ContractConformanceIT.java`, which compares the paths, methods and component schema names of springdoc `/v3/api-docs` with `specs/002-architecture-visualizer/contracts/openapi.yaml` and fails on drift (constitution C4)
- [X] T101 Add a production bundle option to `backend/pom.xml`: a `bundle-frontend` Maven profile that runs the frontend build and copies `frontend/dist` into `backend/target/classes/static`, with an SPA fallback controller in `BE/common/SpaForwardController.java`
- [X] T102 Write the Playwright smoke test `frontend/tests/e2e/quickstart.spec.ts`, covering the happy paths of quickstart V1 (paste plus URL), V2 (confirm and delete), V3 (click and field search) and V4 (draft, save, toggle off, export JSON)
- [X] T103 [P] Write the root `README.md`: what the app is, prerequisites, run commands and test commands (link to `specs/002-architecture-visualizer/quickstart.md` rather than duplicating it)
- [X] T104 Run the full quickstart.md validation (V1–V6) and the merge gates (`./mvnw verify`, `npm run typecheck`, `npm test`, `npm run e2e`), and fix any failures

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - **US1 (P1)** comes first: it creates `ServiceSpec`, the parser, `GET /api/graph` and the canvas, which every other story renders on.
  - **US2 (P1)** depends on US1. It extends `GraphService` and the canvas with edges.
  - **US3 (P2)** depends on US1, and on US2 for meaningful edges. Its pure functions (`blastRadius.ts`, `fieldSearch.ts`) and their tests can be written in parallel with US2.
  - **US4 (P2)** depends on US1. The backend scenario work (migration V3, entity, validator, controller) and `scenarioStore`/`overlay` can be built in parallel with US2 and US3. Canvas integration needs the US2 edge components and the US3 highlight functions for FR-037.
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

```text
Setup ─▶ Foundational ─▶ US1 ─┬─▶ US2 ─┬─▶ US3 ─┐
                              │        │        ├─▶ Polish
                              └────────┴─▶ US4 ─┘
```

### Within Each User Story

- Write the tests first and make sure they fail before implementing
- Migrations, entities and records come before services
- Services come before controllers
- Backend endpoints come before frontend hooks, and hooks before components
- The pure frontend functions (overlay, blast radius, search, retargeting) come before the canvas wiring
- Complete each story before moving to the next priority

### Parallel Opportunities

- Setup: the frontend init, tsconfig/Vite, lint and test-config tasks run in parallel once the layout task is done (the backend `pom.xml` task is independent of them)
- Foundational: everything marked [P] (properties, exceptions, Jackson, springdoc, keys, normalizer, generation, Testcontainers base, gen:api, client, canvasStore) is in separate files
- Within each story, all test tasks marked [P] can be written together, and so can migrations and entities
- Backend and frontend tracks within a story can proceed in parallel once the contract is fixed (it already is, in contracts/openapi.yaml)
- After US1, the US4 backend track can run in parallel with US2

---

## Parallel Example: User Story 1

```bash
# Tests for US1 together:
Task: "Spec fixtures in backend/src/test/resources/specs/"
Task: "SpecParserTest in backend/src/test/java/com/refinements/visualizer/ingestion/SpecParserTest.java"
Task: "FieldFlattenerTest in backend/src/test/java/com/refinements/visualizer/ingestion/FieldFlattenerTest.java"
Task: "ServiceRegistrationIT in backend/src/test/java/com/refinements/visualizer/it/ServiceRegistrationIT.java"
Task: "CustomServiceNode.test.tsx in frontend/tests/canvas/CustomServiceNode.test.tsx"

# Persistence and models for US1 together:
Task: "V1__service_spec_and_layout.sql (postgresql + h2)"
Task: "ParsedEndpoint/Field records in .../ingestion/model/"
Task: "ServiceSpec entity + repository in .../service/"
Task: "BaselineLayout entity + repository in .../layout/"
```

## Parallel Example: User Story 2

```bash
Task: "EdgeInferenceEngineTest" ; Task: "ReferencePrecisionTest" ; Task: "GraphServiceTest" ; Task: "EdgeOverrideIT"
Task: "V2__inference_and_overrides.sql" ; Task: "EdgeOverride entity" ; Task: "InferenceSettings entity + initializer"
Task: "InferredEdge/VerifiedEdge components" ; Task: "retargetEdges.ts" ; Task: "EdgeFilterBar.tsx"
```

## Parallel Example: User Story 4

```bash
Task: "overlay.test.ts" ; Task: "scenarioStore.test.ts" ; Task: "ScenarioIT"
Task: "V3__saved_scenario.sql" ; Task: "SavedScenario entity + content records"
Task: "scenarioStore.ts" ; Task: "overlay.ts" ; Task: "exportJson.ts" ; Task: "exportImage.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: run quickstart V1 plus `./mvnw verify` and `npm test`
5. Demo: every service contract browsable on one canvas

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. **US1** → service map (MVP)
3. **US2** → architecture diagram with inferred and verified arrows. This is the main value; the two P1 stories together make the first release meant for real meetings
4. **US3** → blast radius and field search for refinement questions
5. **US4** → Draft Scenario Mode for planning sessions
6. Polish → presentation mode, performance checks, contract conformance, e2e, packaging

### Parallel Team Strategy

With multiple developers, after Setup and Foundational:

- Developer A: US1 backend, then US2 backend (parsing, inference, merge)
- Developer B: US1 frontend, then US2 and US3 frontend (canvas, edges, highlight)
- Developer C, once US1 is merged: the US4 backend and `scenarioStore`/`overlay`, then the US4 canvas integration

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- `frontend/src/api/schema.d.ts` is generated. Re-run `npm run gen:api` whenever `contracts/openapi.yaml` changes, and never edit it by hand
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
