# Microservice Architecture & Scenario Visualizer Constitution

## Core Principles

### I. Spec-First Deterministic Parsing

- All OpenAPI 2.0 (Swagger) and OpenAPI 3.x specifications, in JSON or YAML, MUST be parsed
  on the Java backend using `io.swagger.parser.v3:swagger-parser`.
- Every `$ref` (local, file-relative, and remote where permitted) MUST be fully resolved before
  endpoint and field extraction.
- Swagger 2.0 inputs MUST be converted to the OpenAPI 3.x model through the parser's converter,
  so the rest of the pipeline works on a single canonical model.
- Parsing a spec MUST produce a **Spec Card**: the spec's endpoints and their request and
  response fields. The parser MUST NOT generate edges, link suggestions, or any other
  connection from spec content.
- LLMs or other probabilistic techniques MUST NOT be used to parse, guess, or reconstruct
  schemas.
- Parse failures and unresolved references MUST be reported to the user with the spec location.
  They MUST NOT be silently dropped.

**Rationale**: A card is only useful for search and discussion if it shows exactly what the
spec says. Deterministic parsing gives the same output for the same spec every time, which
makes results reproducible and testable.

### II. Declared Connections

- A **Microservice** is a first-class entity. It MAY exist without a spec, and it MUST have at
  most one linked Spec Card. Spec Cards are imported unlinked. They are linked to or unlinked
  from a microservice only by an explicit user action.
- Connections (edges) MUST run from one microservice to another. Every edge MUST be
  **declared**: it comes from the connections JSON document or is drawn by a user on the
  canvas. The system MUST NOT infer, score, or automatically create edges.
- The **connections document** is a JSON document describing the microservices' connections.
  Users MUST be able to upload it, edit it in the app, and export it. Edges drawn on the canvas
  and edges from the document are the same data: exporting and then re-importing MUST give the
  same set of connections.
- An uploaded or edited connections document MUST be validated on the backend. Invalid
  documents MUST be rejected with an error that points to the offending entry. A partially
  valid document MUST NOT be applied partially without telling the user.
- Edges MUST be persisted independently of spec cards. Re-importing, re-syncing, unlinking, or
  deleting a spec card MUST NOT change edges. An edge whose microservice no longer exists MUST
  be flagged as dangling for review, not deleted automatically.

**Rationale**: The team, not the tool, knows how services talk to each other. Declared,
round-trippable connections keep that knowledge explicit, reviewable, and versionable outside
the app. Inferred guesses added noise and eroded trust.

### III. Readable Canvas Layout

- Every canvas MUST support automated hierarchical layout (for example ELKjs) on demand. The
  layout MUST cover microservice nodes and spec cards, and keep a linked card visually
  attached to its microservice.
- Node and card positions dragged by a user MUST be persisted on save and MUST take precedence
  over auto-layout. Re-running auto-layout MUST be an explicit user action and MUST NOT silently
  overwrite saved positions.

**Rationale**: A diagram that turns into a spiderweb communicates nothing. Respecting human
layout choices keeps the canvas readable and stable between sessions.

### IV. Scenario Sandbox Isolation

- Teams MUST be able to enter a **Scenario Draft Mode** to add proposed microservices,
  proposed spec links, and proposed connections, and to propose changes such as added
  endpoints or fields.
- Changes made in a scenario MUST be stored separately from the baseline (production)
  architecture. Editing a scenario MUST NOT modify baseline microservices, spec cards, links,
  edges, or layout.
- Proposed elements MUST be visually distinguishable from baseline elements on the canvas.
- Scenarios MUST be exportable (for example as a JSON document that can be re-imported) and
  shareable with other team members without requiring them to alter their baseline.
- Promoting scenario changes into the baseline, if supported, MUST be an explicit, reviewable
  action. It MUST NOT happen implicitly.

**Rationale**: The tool exists to discuss "what if" designs during refinement. That only works
if experimentation can never corrupt the shared source of truth.

### V. Backend & Frontend Standards

**Backend**

- Java 21 or later and Spring Boot 3.x.
- REST DTOs MUST be shaped for direct consumption by React Flow (nodes with `id`, `type`,
  `position`, `data`; edges with `id`, `source`, `target`, `type`, `data`), so the frontend
  needs no structural transformation beyond rendering.
- Persistent storage for spec cards, microservices, links, edges, layout positions, and
  scenarios: PostgreSQL in every environment. Local development uses the docker compose
  database; integration tests use a throwaway PostgreSQL (Testcontainers). Schema changes MUST
  be managed through Flyway versioned migrations.
- **Modular backend**: The backend MUST be a Spring Modulith modular monolith.
  - Each module MUST expose only its root-package API, plus explicitly named interfaces.
    Other modules MUST NOT use its internal packages.
  - Module dependencies MUST be declared and MUST be acyclic.
  - Inside each module, code MUST follow ports and adapters: a domain, an application layer
    with inbound and outbound ports, and adapters for the web, persistence and other modules.
  - The domain model of a core module (one that holds business rules) MUST be free of web,
    persistence and JSON framework concerns.
  - Cross-module calls that must share a transaction or feed a response MUST be synchronous.
- JUnit 5 tests MUST cover multi-spec parsing (OpenAPI 2.0 and 3.x, JSON and YAML, cross-file
  `$ref`) and the connections document: validation, import, export, and round-tripping.

**Frontend**

- React 18 or later with TypeScript in `strict` mode. `any` MUST NOT be used without a
  justifying comment.
- `@xyflow/react` for the interactive canvas.
- Microservice nodes and spec cards MUST be visually distinct from each other. Spec cards MUST
  display endpoints and fields cleanly at different zoom levels: show less detail when zoomed
  out and progressively more when zoomed in.
- The browser MUST contain zero business-logic schema parsing. It MUST NOT parse OpenAPI specs
  or resolve `$ref`s. It MAY provide editing aids for the connections document, such as syntax
  highlighting or JSON well-formedness checks. The backend's validation of that document is
  authoritative.

**Rationale**: A single authoritative backend keeps parsing and validation logic testable in
one place. Enforced module boundaries and ports keep that backend changeable as it grows. Strict typing and React Flow-native DTOs keep the frontend thin and predictable.

## Architecture & Data Constraints

- **Single source of truth**: The backend owns all domain state: spec cards, microservices,
  links, edges, layout, and scenarios. The frontend MUST NOT persist domain state locally
  except transient UI state such as zoom, selection, unsaved drags, or an unsaved connections
  document being edited.
- **Spec traceability**: Every spec card, endpoint, and field MUST reference the spec (and
  spec version or upload) it was derived from.
- **Idempotent ingestion**: Importing the same spec content twice MUST NOT duplicate spec
  cards, endpoints, or fields. Importing the same connections document twice MUST NOT
  duplicate microservices or edges.
- **API contracts**: Backend REST endpoints MUST be documented with an OpenAPI description of
  their own. Breaking changes to DTOs consumed by the frontend MUST be versioned or coordinated
  in the same change set.
- **Security baseline**: Uploaded specs and connections documents MUST be treated as untrusted
  input. Remote `$ref` resolution MUST be restricted (allowlist or disabled by default) to
  prevent SSRF, and upload size limits MUST be enforced.

## Development Workflow & Quality Gates

- Every feature MUST go through the Spec Kit flow (`/speckit-specify` → `/speckit-plan` →
  `/speckit-tasks` → `/speckit-implement`). Each plan MUST include a Constitution Check against
  Principles I–V.
- A change MUST NOT be merged unless:
  - backend JUnit 5 tests pass, including the parsing and connections-document suites when
    those areas are touched;
  - the backend modularity test passes (Spring Modulith `ApplicationModules.verify()` plus the
    ports-and-adapters rules);
  - the frontend type-checks under `strict` mode with no errors and its tests pass;
  - changes to the connections document format include fixtures that prove valid documents
    import and round-trip, and that invalid documents are rejected with precise errors.
- Changes to the edge model, spec linking, or scenario isolation MUST include tests proving
  that edges and baseline data survive spec re-import, unlinking, and scenario edits.
- Any deviation from a principle MUST be recorded in the plan's Complexity Tracking section
  with the justification and the simpler alternative that was rejected.

## Governance

- This constitution supersedes other development practices for this project. Where a guide,
  template, or habit conflicts with it, the constitution wins.
- **Amendments** MUST be made through `/speckit-constitution`, MUST include a Sync Impact Report,
  and MUST state the reason for the change. Amendments that remove or redefine a principle MUST
  describe the migration path for existing features and data.
- **Versioning** follows semantic versioning:
  - MAJOR: backward-incompatible removal or redefinition of a principle or governance rule.
  - MINOR: a new principle or section, or materially expanded guidance.
  - PATCH: clarifications, wording, and typo fixes with no semantic change.
- **Compliance review**: Every plan and code review MUST verify compliance with this
  constitution. Reviewers MUST reject changes that violate a principle without a documented
  justification.

**Version**: 2.1.0 | **Ratified**: 2026-11-21 | **Last Amended**: 2026-11-23
