# Feature Specification: Modular Backend (Modulith + Ports & Adapters)

**Feature Branch**: `005-modular-backend`

**Created**: 2026-11-23

**Status**: Implemented (2026-11-23)

**Input**: User description: "Restructure the backend into a Spring Modulith modular monolith where each module follows a ports-and-adapters (hexagonal) layout. Pure refactor: REST API, database schema and frontend behavior must not change. Modules: shared, workspace, card (ingestion folded in), microservice, connection, layout, note, scenario, canvas. Break the microservice↔connection and card↔microservice cycles with synchronous ports. Core modules get plain domain models separate from persistence; small modules may keep persistence entities as their model. Add an automated modularity check and generated module documentation. Existing integration tests must pass unchanged. Initialize version control and make a baseline commit first. Migrate one module at a time, starting with note."

> **Nature of this feature.** This is an internal restructuring. Its "users" are the people who
> maintain the backend. End users of the visualizer MUST notice no difference at all. Because the
> requested outcome *is* a code structure, this spec names the target modules and boundaries; the
> plan decides the concrete classes, packages and library versions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Safe starting point and enforced boundaries (Priority: P1)

A maintainer puts the project under version control with a baseline snapshot, then adds an
automated check that knows the intended modules and fails the build whenever one module reaches
into another module's internals or two modules depend on each other. After the cycles and the
direct cross-module data access are removed, the check passes and the app behaves exactly as
before.

**Why this priority**: Without a baseline the refactor cannot be reviewed or rolled back. Without
an automated boundary check, any later cleanup erodes again. This story alone already delivers a
backend whose module boundaries are real and guarded.

**Independent Test**: Run the full backend test suite and the modularity check. The suite passes
unchanged; the check passes; deliberately adding a forbidden dependency (e.g. the canvas reading
another module's storage directly) makes the check fail.

**Acceptance Scenarios**:

1. **Given** the project has no version control, **When** the feature starts, **Then** a repository exists with one baseline commit of the current, working code (without build outputs, dependencies or local data folders).
2. **Given** the modularity check is added before any restructuring, **When** it runs, **Then** it reports the current violations (the two dependency cycles and the direct cross-module data access) as a recorded baseline.
3. **Given** the cycles are broken and the canvas, layout and other modules use only each other's public interfaces, **When** the modularity check runs, **Then** it passes with zero violations.
4. **Given** the boundaries are enforced, **When** every existing backend unit and integration test runs, **Then** all pass without changing any test's assertions or expected API responses.

---

### User Story 2 - Ports and adapters inside each module (Priority: P2)

A maintainer opens any module and finds the same layout: a small public interface at the module
root, the business rules in a domain area free of web and storage concerns, use cases that state
what the module can do (inbound ports) and what it needs from outside (outbound ports), and
adapters that connect those ports to the web, the database, the spec fetcher/parser and other
modules.

**Why this priority**: This is the long-term maintainability goal. It builds on Story 1 and can be
done one module at a time, with the app working after every step.

**Independent Test**: For each converted module, its business rules can be tested with in-memory
stand-ins for its outbound ports (no database, no web server), and the full integration suite
still passes.

**Acceptance Scenarios**:

1. **Given** the note module is converted first as the template, **When** a maintainer compares it with any module converted later, **Then** both follow the same layout and naming conventions.
2. **Given** a core module (connection, microservice, card, scenario) is converted, **When** its domain rules are inspected, **Then** they do not depend on web or storage frameworks, and storage records are translated to and from the domain model at the storage adapter.
3. **Given** a small module (note, layout, workspace) is converted, **When** it is inspected, **Then** its storage records may double as its model, but other code in the module reaches storage only through its outbound ports.
4. **Given** any module is converted, **When** the full integration suite and the modularity check run, **Then** both pass.

---

### User Story 3 - Living module documentation (Priority: P3)

A maintainer or new contributor can read up-to-date diagrams and a description of the modules,
their public interfaces and the dependencies between them, generated from the code rather than
drawn by hand.

**Why this priority**: Helpful for onboarding and reviews, but the system works without it.

**Independent Test**: Run the documentation step; it produces an overview diagram of all modules
and one view per module, matching the dependencies the modularity check reports.

**Acceptance Scenarios**:

1. **Given** the restructuring is complete, **When** the documentation step runs, **Then** it produces a diagram of all modules and their allowed dependencies, plus a per-module view.
2. **Given** a dependency between modules changes, **When** the documentation is regenerated, **Then** the diagrams reflect the change without manual editing.

---

### Edge Cases

- **Renaming or deleting a microservice** still detaches and reattaches dangling connection ends in the same all-or-nothing step, and the response still lists the reattached connections and the new workspace revision.
- **Deleting a spec card** still unlinks the microservice that was linked to it in the same step; a card summary still shows which microservice it is linked to.
- **Concurrent edits**: the workspace revision check (stale connections document → conflict) and the serialization of microservice/connection writes behave exactly as before.
- **Errors**: every error response (status code, message text, document error locations) is unchanged, even though error types move to a shared area.
- **Connections document import** remains all-or-nothing; a failure part-way through leaves no partial changes.
- **Scenario isolation**: scenarios still read the baseline only through a read-only view and never change baseline data or the workspace revision.
- **Existing data**: the live database is used as-is; no migration runs and no table, column or constraint changes.
- **Performance**: the canvas and document endpoints are not noticeably slower after extra mapping between storage and domain models.

## Requirements *(mandatory)*

### Functional Requirements

**Baseline and safety**

- **FR-001**: Before any restructuring, the project MUST be placed under version control with a baseline commit of the current working code, excluding build outputs, installed dependencies, test outputs and local database folders (including `backend/data` and its backups, which stay on disk untouched).
- **FR-002**: The restructuring MUST NOT change any REST endpoint, request or response shape, status code, error message, or the published API description.
- **FR-003**: The restructuring MUST NOT change the database schema or require any data migration; the existing schema script stays the only one.
- **FR-004**: All existing backend tests (unit and integration, including the API contract conformance test) MUST pass with unchanged assertions. Tests MAY only be moved, have imports updated, or have data-setup code updated to follow moved or renamed classes; assertions MUST NOT change.
- **FR-005**: The frontend MUST need no changes, and its end-to-end tests MUST pass against the restructured backend.

**Modules and boundaries**

- **FR-006**: The backend MUST be organized into these modules: *shared* (errors, patch helper, positions, name rules, configuration — usable by all modules), *workspace* (revision), *card* (spec cards, including spec fetching and parsing), *microservice*, *connection* (connections, flows, connections document), *layout*, *note*, *scenario*, and *canvas*.
- **FR-007**: Each module MUST expose a deliberate public interface; everything else in the module MUST be internal and unreachable from other modules.
- **FR-008**: Module dependencies MUST form no cycles. The intended direction is: card ← microservice ← connection; layout, note, workspace and shared have no dependencies on feature modules other than those they need; canvas and scenario sit on top and only read.
- **FR-009**: The microservice↔connection cycle MUST be removed: the microservice module MUST declare what it needs done to connection ends (detach, reattach by name and report which ones) as an outbound port, implemented by the connection module; the connection module MUST read microservices only through the microservice module's public interface.
- **FR-010**: The card↔microservice cycle MUST be removed: the microservice module MUST check card existence through the card module's public interface; the card module MUST declare what it needs about card links (which microservice links a card; unlink on card deletion) as an outbound port, implemented by the microservice module.
- **FR-011**: Cross-module calls that change data or feed a response (detach/reattach, unlink, revision bump and check) MUST run synchronously within the caller's single all-or-nothing operation.
- **FR-012**: The canvas and layout modules MUST obtain other modules' data only through those modules' public interfaces, never through their storage directly.
- **FR-013**: An automated modularity check MUST run as part of the normal backend test run and fail on any boundary violation or cycle.

**Ports and adapters**

- **FR-014**: Every module MUST follow one layout: public interface at the module root; domain; application (use cases) with inbound and outbound ports; adapters for inbound traffic (web) and outbound needs (storage, spec fetching/parsing, other modules).
- **FR-015**: In the connection, microservice, card and scenario modules, the domain model MUST be free of web and storage framework concerns and be translated to and from storage records at the storage adapter.
- **FR-016**: In the note, layout and workspace modules, storage records MAY serve as the domain model, but use cases MUST reach storage only through outbound ports.
- **FR-017**: Spec fetching and spec parsing MUST become outbound adapters of the card module behind ports; parsing behavior (Principle I) MUST stay byte-for-byte equivalent in output.
- **FR-018**: Modules MUST be converted one at a time, note first as the template, with the full test suite and modularity check passing after each module.

**Documentation**

- **FR-019**: Module documentation (overview diagram, per-module views) MUST be generated from the code by a repeatable step and kept with the feature's or project's docs.
- **FR-020**: The project constitution's backend standards MUST be updated (via the constitution process) to record the modular, ports-and-adapters structure as the expected backend architecture, and to correct the outdated statement that local development uses H2.

### Key Entities

- **Module**: a named, bounded part of the backend with a public interface and internal parts; the unit the modularity check reasons about.
- **Public interface (of a module)**: the operations and data shapes other modules may use.
- **Inbound port**: a use case a module offers (e.g. "create connection", "import connections document").
- **Outbound port**: something a module needs from outside (e.g. "store connections", "detach connection ends", "find which microservice links a card").
- **Adapter**: the piece that connects a port to the outside world — web endpoints, the database, the spec fetcher/parser, or another module.
- **Domain model**: the business data and rules of a module, independent of web and storage.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the backend tests that passed before the feature pass after it, with zero changed assertions.
- **SC-002**: The frontend end-to-end suite passes against the restructured backend with zero frontend code changes.
- **SC-003**: The modularity check reports 0 cycles and 0 boundary violations, and fails when a forbidden dependency is introduced on purpose.
- **SC-004**: 9 modules exist, and every one follows the same layout; a reviewer can find any module's use cases, ports and adapters in the same place in under a minute.
- **SC-005**: For each of the 4 core modules, at least one business-rule test runs with no database and no web server.
- **SC-006**: Typical operations (loading the canvas, editing a connection, importing the reference connections document) are no more than 10% slower than before, measured with the existing performance smoke test.
- **SC-007**: Every step of the migration (one module at a time) is a separate commit after which the app builds and all tests pass.

## Assumptions

- The live Postgres database (docker-compose volume `db-data`) is used unchanged; it is never dropped. `backend/data` and `backend/data.backup-*` stay on disk and are excluded from version control.
- The modular-monolith framework version is chosen to match the backend's current application framework (Spring Boot 3.5 → Spring Modulith 1.4.x); no framework upgrade is part of this feature.
- Async, event-based communication between modules is out of scope; it may be added later for side effects that do not need to share the operation or feed a response.
- The single-process deployment (one backend, one database, the current Docker setup) stays as is; no module is split into a separate service.
- Version control is local only; pushing to a remote is not part of this feature.
- Developers may adjust test imports when classes move; the integration tests themselves remain the behavioral safety net.
