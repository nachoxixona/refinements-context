# Feature Specification: Microservice Architecture & Scenario Visualizer

**Feature Branch**: `002-architecture-visualizer`

**Created**: 2026-11-21

**Status**: Draft

**Input**: User description: "Build a collaborative Microservice Architecture & Scenario Visualizer
web application. Core User Flows: 1. Service Ingestion: Developers can register a microservice by
pasting raw OpenAPI/Swagger (JSON or YAML) or providing a live Swagger URL, plus trigger a re-sync
when an API changes. 2. Interactive Architecture Canvas: Each microservice appears as a custom node
displaying its service name, HTTP method badges, endpoints, and expandable request/response schema
fields. 3. Automatic & Manual Connections: The system automatically draws dashed directional arrows
from a service's output response fields to another service's matching input parameters or request
body fields (ignoring generic fields like id or status). Users can click an inferred edge to
confirm it (turning it solid), delete false positives, or manually draw new connections between
services. 4. Blast-Radius Highlighting & Search: Users can search for a specific field name (e.g.,
"customerId") or click any service node to dim unrelated services and highlight only direct
upstream producers and downstream consumers. 5. Scenario Planning Mode: Users can toggle "Draft
Scenario Mode" to add temporary proposed microservices, new endpoints, and green dashed
connections to plan new features during team meetings, without altering the baseline
architecture, and export the view as JSON or an image."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register services and see them on the canvas (Priority: P1)

A developer registers a microservice either by pasting the raw text of its OpenAPI/Swagger spec
(JSON or YAML) or by entering the URL where the service publishes its live spec. The service
appears on the canvas as a node showing the service name and its endpoints, each with a
colored HTTP method badge (GET, POST, PUT, PATCH, DELETE). The developer can expand an endpoint
to see its request and response fields. When the API changes, the developer triggers a re-sync
and the node updates.

**Why this priority**: Every other flow depends on services being on the canvas. On its own, it
already gives the team one place to browse every service contract.

**Independent Test**: Register one service by pasting a Swagger 2.0 YAML spec and another by URL
to an OpenAPI 3.x JSON spec. Confirm both nodes show the right name, method badges and
endpoints, that expanding an endpoint shows its fields, and that a re-sync after changing the
URL's spec updates the node.

**Acceptance Scenarios**:

1. **Given** the registration form, **When** a developer pastes a valid OpenAPI 3.x spec in YAML
   and submits it, **Then** a node appears with the service name and every endpoint with its
   method badge and path.
2. **Given** the registration form, **When** a developer enters a reachable URL to a Swagger 2.0
   JSON spec, **Then** the spec is fetched and the service appears just as if it had been pasted.
3. **Given** a node on the canvas, **When** the user expands an endpoint, **Then** its request
   fields (path, query and header parameters, and body) and its response fields are listed with
   their names and types, including nested fields.
4. **Given** a spec whose fields are defined through references to shared definitions, **When**
   it is registered, **Then** the node shows the fully expanded fields, not the reference names.
5. **Given** invalid text or a spec with references that cannot be resolved, **When** it is
   submitted, **Then** the developer sees an error that names the location of the problem, and
   no service is added.
6. **Given** a service registered from a URL whose spec has changed, **When** the developer
   triggers a re-sync, **Then** the node reflects the new endpoints and fields.
7. **Given** a service registered by pasting, **When** the developer pastes an updated spec for
   that service, **Then** the existing service is updated instead of a duplicate being created.
8. **Given** many services on the canvas, **When** the user zooms out, **Then** nodes collapse to
   their name and endpoint count so the overall map stays readable.
9. **Given** services on the canvas, **When** the user drags nodes and the layout is saved, or
   chooses "Auto-arrange", **Then** the saved positions are restored on reload, and auto-arrange
   only replaces them when explicitly chosen.

---

### User Story 2 - Automatic and manual connections (Priority: P1)

Once services are registered, the system draws dashed arrows from an endpoint whose response
contains a field to endpoints in other services that take a matching field as an input parameter
or in their request body. Generic fields such as `id` or `status` are ignored. A user clicks an
inferred arrow to see why it was drawn, then confirms it (it turns solid) or deletes it as a
false positive. Users can also draw connections by hand.

**Why this priority**: The arrows turn a list of services into an architecture diagram. They are
the core value of the product.

**Independent Test**: Register service A, where `GET /orders/{orderId}` returns `customerId`, and
service B, where `GET /customers/{customerId}` takes `customerId`. Confirm a dashed arrow from A
to B appears and that no arrow is drawn just because both share `id` or `status`. Confirm the
arrow and check it turns solid; delete another and check it does not come back after a re-sync.

**Acceptance Scenarios**:

1. **Given** two services where a response field of one matches an input of another, **When**
   both are registered, **Then** a dashed arrow points from the producing endpoint to the
   consuming endpoint.
2. **Given** two services that only share generic fields (`id`, `status`, `timestamp`,
   `message`, and similar), **When** both are registered, **Then** no arrow is drawn because of
   those fields.
3. **Given** an inferred arrow, **When** the user clicks it, **Then** they see the matched field
   pair(s) and a confidence score, plus options to confirm or delete.
4. **Given** an inferred arrow, **When** the user confirms it, **Then** it becomes a solid
   arrow.
5. **Given** an inferred arrow, **When** the user deletes it as a false positive, **Then** it
   disappears and does not reappear after either service is re-synced.
6. **Given** two services, **When** the user draws a connection between them by hand, **Then** a
   solid arrow is created. If the user picks specific endpoints, the arrow connects those
   endpoints; otherwise it connects the services.
7. **Given** confirmed or hand-drawn connections, **When** a service involved is re-synced,
   **Then** those connections remain. If an endpoint they use no longer exists, the connection
   is flagged for review instead of being deleted.

---

### User Story 3 - Blast radius and field search (Priority: P2)

In a meeting, someone asks "what does this service touch?" A user clicks the service node. Its
direct upstream producers (services that feed it data) and direct downstream consumers (services
that use its data) are highlighted, along with the arrows between them, and everything else is
dimmed. Alternatively, the user searches for a field name such as `customerId` and sees every
service and endpoint that produces or consumes that field.

**Why this priority**: This answers the most common refinement question, "if we change this,
who is affected?" It needs Stories 1 and 2 first.

**Independent Test**: Set up A → B → C plus an unrelated service D. Click B: A is highlighted as
upstream, C as downstream, D is dimmed. Search for a field used by A and C: both are highlighted
with the matching endpoints marked.

**Acceptance Scenarios**:

1. **Given** A → B → C and an unrelated service D, **When** the user clicks B, **Then** A is
   highlighted as upstream, C as downstream (each in a distinct style), and D is dimmed.
2. **Given** A → B → C → E, **When** the user clicks B, **Then** only direct neighbours (A and C)
   are highlighted; E is dimmed.
3. **Given** a highlighted selection, **When** the user clicks an empty part of the canvas or
   presses Escape, **Then** all services return to normal.
4. **Given** services that use `customerId`, **When** the user searches for `customerId`,
   **Then** every service that has the field in an input or output is highlighted, the matching
   endpoints and fields are marked, each match shows whether it is produced or consumed, and
   all other services are dimmed.
5. **Given** a search that matches nothing, **When** it runs, **Then** the user is told there are
   no matches and the canvas is unchanged.
6. **Given** search results, **When** the user picks a result from the list, **Then** the canvas
   centers on that service.

---

### User Story 4 - Draft Scenario Mode (Priority: P2)

To plan a new feature during a team meeting, a user toggles Draft Scenario Mode. The baseline
architecture stays visible, but the user can now add proposed microservices, add new endpoints
(to proposed or existing services), and draw proposed connections, which appear as green
dashed arrows. None of this changes the baseline. The team saves the scenario, opens it again
later, and exports it as JSON or as an image to share.

**Why this priority**: This is what makes the tool useful for planning rather than only
documenting. It builds on the canvas and connections.

**Independent Test**: Toggle Draft Scenario Mode, add a proposed service with one endpoint,
connect it to an existing service with a green dashed arrow, then toggle the mode off. The
baseline looks exactly as before. Export the scenario as JSON and as an image; re-import the
JSON and confirm the scenario is reproduced.

**Acceptance Scenarios**:

1. **Given** the baseline canvas, **When** the user toggles Draft Scenario Mode on, **Then** the
   canvas shows a clear indicator that a draft is active and the baseline remains visible.
2. **Given** Draft Scenario Mode, **When** the user adds a proposed service with a name and
   endpoints (method, path, and optional fields), **Then** it appears with "proposed" styling,
   visually distinct from baseline services.
3. **Given** Draft Scenario Mode, **When** the user adds a new endpoint to an existing baseline
   service, **Then** the endpoint is shown as proposed on that service in the scenario only.
4. **Given** Draft Scenario Mode, **When** the user draws a connection, **Then** it appears as a
   green dashed arrow, distinct from baseline inferred (dashed) and confirmed (solid) arrows.
5. **Given** a scenario with proposed changes, **When** the user toggles Draft Scenario Mode off,
   **Then** the baseline services, connections and positions are exactly as they were before.
6. **Given** a scenario, **When** the user saves it with a name, **Then** it can be reopened
   later from the list of scenarios.
7. **Given** a scenario, **When** the user exports it as JSON, **Then** they get a file that can
   be imported back to reproduce the scenario.
8. **Given** a scenario, **When** the user exports it as an image, **Then** they get an image of
   the current canvas view including proposed elements and their styling.
9. **Given** a scenario, **When** the user clicks a service, **Then** blast-radius highlighting
   includes proposed services and connections.
10. **Given** a saved scenario that references a baseline endpoint that was later removed by a
    re-sync, **When** the scenario is opened, **Then** the affected proposed connections are
    flagged.

---

### Edge Cases

- A spec has no endpoints: the node is shown with a "no endpoints" indicator.
- A pasted spec is for a service that is already registered under the same name: the user is
  asked whether to update the existing service or register it as a separate one.
- A spec is very large (hundreds of endpoints): the node's endpoint list is collapsed and
  scrollable, and registration either completes or reports an error rather than hanging.
- A URL is unreachable, requires credentials, or returns something that is not a spec: the user
  gets a clear error, and on re-sync the last successfully registered version is kept.
- A spec references external files or URLs: references to sources that are not allowed are not
  followed and are reported as unresolved.
- Pasted text exceeds the size limit: it is rejected with a clear message.
- A non-generic field name matches many endpoints across services: the number of inferred
  arrows per field is limited and the user is told that further candidates were hidden.
- A user deletes an inferred arrow and later wants it back: dismissed arrows can be listed and
  restored.
- Two services connect in both directions (A → B and B → A): clicking either one highlights the
  other as both upstream and downstream, without duplicates.
- A user draws a connection from a service to itself: this is not allowed.
- The app is open in two browser tabs and both save changes: the later save is warned that the
  content changed since it was loaded, instead of silently overwriting it.

## Requirements *(mandatory)*

### Functional Requirements

**Service ingestion**

- **FR-001**: Users MUST be able to register a service by pasting raw spec text in JSON or YAML.
- **FR-002**: Users MUST be able to register a service by providing the URL of its live spec,
  which the system fetches.
- **FR-003**: The system MUST accept Swagger 2.0 and OpenAPI 3.x specs.
- **FR-004**: The system MUST fully expand all references in a spec, so that every endpoint's
  request and response fields are shown in full.
- **FR-005**: The system MUST reject invalid specs with an error naming the location of the
  problem, and MUST NOT add partial services without telling the user.
- **FR-006**: Users MUST be able to re-sync a URL-registered service on demand, and to update a
  pasted service by pasting a new version. Updating MUST NOT create a duplicate service.
  Re-syncing identical content MUST change nothing.
- **FR-007**: If a re-sync fails, the system MUST keep the last successfully registered version
  and show the error.
- **FR-008**: The system MUST enforce a maximum spec size and MUST only follow external
  references to allowed sources.
- **FR-009**: Users MUST be able to remove a registered service.

**Canvas and nodes**

- **FR-010**: Each service MUST be shown as a node with its name and its endpoints, each with
  an HTTP method badge whose color identifies the method, and its path.
- **FR-011**: Users MUST be able to expand and collapse an endpoint to see its request fields
  (parameters and body) and response fields, with names, types and nesting.
- **FR-012**: When zoomed out, nodes MUST collapse to name and endpoint count; when zoomed in,
  endpoints and fields MUST be readable.
- **FR-013**: Users MUST be able to pan, zoom, select and drag nodes.
- **FR-014**: Users MUST be able to auto-arrange the canvas into a hierarchical layout on
  request. Saved node positions MUST be restored on reload and only replaced by an explicit
  auto-arrange.

**Connections**

- **FR-015**: The system MUST infer directional connections from an endpoint's response fields
  to matching input parameters or request body fields of endpoints in other services.
- **FR-016**: The system MUST exclude generic fields from inference using a configurable list.
  The default list MUST include `id`, `status`, `timestamp`, `message`, `createdAt`,
  `updatedAt`, `error` and `code`.
- **FR-017**: Each inferred connection MUST record the matched field pair(s) and a confidence
  score, shown when the user clicks it.
- **FR-018**: Inferred connections MUST be shown as dashed arrows; confirmed and hand-drawn
  connections MUST be shown as solid arrows. All arrows MUST show their direction.
- **FR-019**: Users MUST be able to confirm an inferred connection, which makes it solid.
- **FR-020**: Users MUST be able to delete an inferred connection as a false positive. The
  deletion MUST persist so the connection is not re-inferred after re-syncs, and users MUST be
  able to view and restore deleted connections.
- **FR-021**: Users MUST be able to draw a connection by hand between two different services,
  optionally between specific endpoints.
- **FR-022**: Users MUST be able to delete confirmed and hand-drawn connections.
- **FR-023**: Confirmed and hand-drawn connections MUST survive re-syncs. A connection whose
  endpoint no longer exists MUST be flagged for review, not deleted automatically.

**Blast radius and search**

- **FR-024**: Clicking a service MUST highlight its direct upstream producers and direct
  downstream consumers and the connections between them, and MUST dim all other services.
- **FR-025**: Upstream and downstream services MUST be shown in visually distinct styles.
- **FR-026**: Clicking an empty area of the canvas or pressing Escape MUST clear the highlight.
- **FR-027**: Users MUST be able to search by field name. Results MUST highlight every service
  whose endpoints produce or consume that field, mark the matching endpoints and fields, show
  whether each match is an input or an output, and dim all other services.
- **FR-028**: Field search MUST be case-insensitive and match nested field names (for example,
  a search for `customerId` matches `order.customerId`).
- **FR-029**: Users MUST be able to search for services and endpoints by name and jump to them
  on the canvas.

**Draft Scenario Mode**

- **FR-030**: Users MUST be able to toggle Draft Scenario Mode on and off, and the canvas MUST
  clearly show when it is active.
- **FR-031**: In Draft Scenario Mode, users MUST be able to add proposed services, add proposed
  endpoints (with method, path and optional fields) to proposed or baseline services, draw
  proposed connections, move nodes, and remove what they proposed.
- **FR-032**: Proposed services and endpoints MUST be visually distinct from baseline ones.
  Proposed connections MUST be shown as green dashed arrows.
- **FR-033**: Changes made in Draft Scenario Mode MUST NOT alter baseline services, connections
  or layout.
- **FR-034**: Users MUST be able to save a scenario with a name, list saved scenarios, reopen,
  rename and delete them. In v1 a single person edits at a time (see FR-039).
- **FR-035**: Users MUST be able to export a scenario as a JSON file and import that file to
  reproduce the scenario.
- **FR-036**: Users MUST be able to export the current canvas view, baseline or scenario, as an
  image.
- **FR-037**: Blast-radius highlighting and search MUST include proposed elements while in Draft
  Scenario Mode.
- **FR-038**: When a scenario is opened, proposed elements that depend on baseline endpoints
  that no longer exist MUST be flagged.

**Single-editor use**

- **FR-039**: v1 MUST support one editor at a time: a single developer edits the diagram while
  sharing their screen and the rest of the team watches. Shared or simultaneous editing is not
  required.
- **FR-040**: The canvas MUST stay readable when shown over a screen share: method badges,
  service names, arrow styles and highlight states MUST be distinguishable at a typical
  meeting-share resolution, and the editor MUST be able to switch to a full-screen canvas view.
- **FR-041**: The system MUST record when each connection was confirmed, deleted or drawn and
  when each scenario was created or last changed.

### Key Entities

- **Workspace**: The space holding a team's baseline architecture: services,
  connections, layout, generic-field settings and scenarios.
- **Spec Source**: Where a service's spec came from (pasted text or URL), its format and version,
  when it was last synced, and the result of the last sync.
- **Service**: A microservice with a name, version and endpoints. Baseline services come from a
  spec source; proposed services exist only within a scenario.
- **Endpoint**: An operation identified by HTTP method and path, with input fields (parameters
  and request body) and output fields (response body).
- **Field**: A named, typed piece of data in an input or output, including nested fields with
  their full path (for example `order.customerId`).
- **Connection**: A directed link from one service or endpoint (producer) to another (consumer).
  It is inferred (with matched fields and confidence score), confirmed, hand-drawn, or proposed
  (scenario only). It can be flagged for review.
- **Dismissal**: A record that an inferred connection was deleted as a false positive, so it is
  not re-inferred.
- **Layout**: Saved node positions for the baseline or a scenario.
- **Generic Field List**: Field names excluded from inference.
- **Draft Scenario**: A named set of proposed services, endpoints and connections over the
  baseline, with its own layout. It can be saved, reopened and exported.

## Clarifications

### Session 2026-11-21

- Q: How do team members collaborate on the workspace and scenarios? → A: In v1 one developer
  edits the diagram while sharing their screen; the others only watch and talk. Shared editing
  is deferred to a later release.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can register a service by paste or URL and see it on the canvas in
  under 1 minute.
- **SC-002**: A spec with up to 100 endpoints appears on the canvas within 10 seconds of
  submission.
- **SC-003**: On a reference set of specs with known real connections, at least 80% of inferred
  arrows are judged correct by the team, and no arrow is drawn only because of a field on the
  generic list.
- **SC-004**: 100% of confirmed, hand-drawn and deleted connections keep their state after every
  service is re-synced.
- **SC-005**: Clicking a service or running a field search highlights the results within 1
  second in a workspace of 50 services.
- **SC-006**: The canvas stays smooth to pan, zoom and drag with 50 services and 500 connections.
- **SC-007**: In 100% of tested scenario edits, the baseline is identical after Draft Scenario
  Mode is toggled off.
- **SC-008**: During a meeting, the presenting developer can sketch a scenario with one proposed service and two
  proposed connections and export it as an image in under 3 minutes.
- **SC-009**: Participants can answer "which services produce or consume `customerId`?" in under
  15 seconds using field search.

## Assumptions

- Users are developers and architects within one organization, working in one team workspace.
  Multiple workspaces and per-team permissions are out of scope for v1.
- In v1, one developer edits the diagram while sharing their screen in a meeting; the others
  watch and talk. Shared editing (several people editing the same workspace or scenario, live
  or not) is deferred to a later release.
- Because there is a single editor, v1 needs no user accounts, sign-in or per-user attribution.
  Anyone who can open the app can edit, including the generic field list. The app is assumed to
  run locally or on the internal network.
- Teammates receive scenarios through the JSON or image export, not through shared access.
- The app runs on the organization's internal network. Spec URLs must be reachable from the
  server without credentials in v1; private specs can be pasted instead.
- Specs up to 10 MB are supported. External references are not followed by default and can be
  allowed for a list of trusted sources.
- Re-sync is triggered manually; scheduled or automatic polling is out of scope for v1.
- A service is identified by its spec title (and server address if present); updates match on
  this identity.
- Connections are inferred only between different services, not within one service.
- Blast radius covers direct neighbours only (one hop), as requested. Multi-hop impact analysis
  is out of scope for v1.
- Image export captures the current view in a common image format at screen resolution.
- Merging a scenario into the baseline is out of scope for v1; the baseline changes only through
  ingestion and connection edits.
- Formats other than Swagger 2.0 and OpenAPI 3.x (for example AsyncAPI, gRPC, GraphQL) are out of
  scope.
- Desktop browsers are the target; mobile layouts are out of scope.
