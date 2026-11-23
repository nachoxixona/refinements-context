# Feature Specification: Declared Connections & Spec Cards

**Feature Branch**: `003-declared-connections`

**Created**: 2026-11-22

**Status**: Draft

**Supersedes**: `specs/002-architecture-visualizer` (its inference model; see constitution v2.0.0)

**Input**: User description: "Rework the Microservice Architecture & Scenario Visualizer around
declared connections (constitution v2.0.0), superseding the inference model of feature 002.
Core User Connections: 1. Spec Card Import: Developers import an OpenAPI/Swagger spec (pasted
JSON/YAML or a live URL, with re-sync). Each import becomes an isolated Spec Card on the canvas
showing the spec's title, endpoints with HTTP method badges, and expandable request/response
fields. Importing never creates connections. 2. Microservice Nodes: Users create microservice
nodes on the same canvas. A microservice can exist with no spec. A microservice has at most one
linked spec card, and the user links or unlinks a card to a microservice explicitly; a linked
card stays visually attached to its microservice. 3. Declared Connections: Connections run only
from one microservice to another. They are described in a connections JSON document that users
can upload, edit in the app, and export. Users can also draw and delete connections on the
canvas; drawn connections and JSON connections are the same data, so exporting and re-importing
gives the same connections. Invalid documents are rejected with precise errors. Connections
whose microservice no longer exists are flagged, not deleted. 4. Search & Highlighting: Users
search a field or endpoint name (e.g. "customerId") across all spec cards on the canvas, and see
matching cards and the microservices they are linked to. Clicking a microservice dims unrelated
ones and highlights its direct upstream and downstream connected microservices. 5. Scenario
Planning Mode: Draft Scenario Mode stays: users add proposed microservices, proposed spec links,
proposed connections, and proposed endpoints/fields, without altering the baseline, and export
the view as JSON or an image. 6. Migration: Existing data from feature 002 is migrated — each
registered service becomes a microservice linked to a spec card built from its stored spec,
manually drawn/confirmed edges become declared connections, inferred edges, dismissals and
inference settings are discarded, layout positions and saved scenarios are kept or reported if
they cannot be carried over."

## Clarifications

### Session 2026-11-22

- Q: When someone uploads a connections document, should it replace all existing connections or be merged into them? → A: Replace. The uploaded document becomes the complete set of baseline connections; connections missing from it are removed after the preview is confirmed.
- Q: Should the connections document also contain a list of microservices, so it can describe microservices that have no connections yet? → A: Yes, an optional `microservices` list (name plus optional description). Export always includes every microservice; connections may still name microservices not in the list; microservices absent from the document are never deleted.
- Q: How should a linked spec card appear next to its microservice on the canvas? → A: Docked. The card stays a separate box docked directly below its microservice and moves with it; it can be collapsed to a small "API: <title>" badge on the microservice.
- Q: Should each connection also record what kind of interaction it is, and be drawn differently for each kind? → A: Yes, an optional fixed `kind`: `request` (synchronous call) or `event` (asynchronous message), drawn with different line styles; connections without a kind are drawn neutrally.
- Q: In Draft Scenario Mode, should the team be able to propose removing or changing existing baseline elements, or only additions? → A: Additions plus proposed removals of baseline connections, microservices and spec links, and proposed changes to a baseline connection's name, kind or description; shown with distinct styling and stored only in the scenario.

### Session 2026-11-22 (connection colors)

- Q: How should connection colors work, so related connections are easy to follow? → A: Named color flows. The team defines flows such as "Checkout" (blue) or "Refunds" (orange) and assigns connections to a flow; the legend lists the flows, recoloring a flow recolors all its connections, and selecting a flow highlights its connections.
- Q: When a connection has an associated API, which API can be picked? → A: One endpoint of the target microservice's spec card (for example `POST /invoices` on Billing), shown on the connection's label and stored as text; if it is no longer in the target's spec, the connection is flagged, not changed.

### Session 2026-11-22 (terminology: connections and flows)

- Q: What is a flow, and what is a connection? → A: A **connection** is a link from one microservice to
  another. A **flow** is a named, colored business process that runs through several connections (for
  example "Checkout"). What earlier sessions of this spec called "flows" are connections, and what they
  called "groups" are flows; the spec, the API, the database, the connections document (`formatVersion` 2;
  version 1 files still import) and scenario documents (`formatVersion` 3; versions 1 and 2 still import) use
  these names.
- Q: How is a connection's endpoint shown? → A: Explicitly on the diagram, as a badge near the arrowhead
  with the HTTP method badge and the path (only the method when zoomed far out), separate from the
  connection's name.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Import specs as spec cards (Priority: P1)

A developer imports an API spec either by pasting its OpenAPI/Swagger text (JSON or YAML) or by
entering the URL where it is published. The spec appears on the canvas as a **spec card**
showing the spec's title and version, and its endpoints, each with a colored HTTP method badge.
The developer can expand an endpoint to see its request and response fields. When the API
changes, the developer re-syncs the card and it updates in place. Importing a spec never
creates or changes connections.

**Why this priority**: Spec cards are the reference material the team searches and discusses.
On their own they already give the team one place to browse every API contract.

**Independent Test**: Import one Swagger 2.0 YAML spec by pasting and one OpenAPI 3.x JSON spec
by URL. Confirm two unlinked cards appear with the right titles, method badges and expandable
fields, and that the canvas has no connections. Change the URL-hosted spec, re-sync, and
confirm the card updates without duplicating.

**Acceptance Scenarios**:

1. **Given** an empty canvas, **When** the developer pastes a valid spec and confirms,
   **Then** a spec card appears showing the spec title, every endpoint with its method badge,
   and expandable request and response fields.
2. **Given** a spec published at a reachable URL, **When** the developer imports it by URL,
   **Then** a spec card appears and remembers its source URL for re-sync.
3. **Given** a spec card imported by URL, **When** the published spec changes and the
   developer re-syncs, **Then** the same card shows the new endpoints and fields, keeps its
   position and its link to a microservice, and no connection changes.
4. **Given** an invalid spec or one with unresolvable references, **When** the developer
   imports it, **Then** the import is rejected with a message naming the problem and its
   location in the spec, and nothing is added to the canvas.
5. **Given** a spec card already exists for some content, **When** the same content is
   imported again, **Then** no duplicate card is created and the developer is told the card
   already exists.
6. **Given** a canvas with existing connections, **When** any spec is imported, re-synced or
   deleted, **Then** the set of connections is unchanged.

---

### User Story 2 - Create microservices and link spec cards (Priority: P1)

A team member creates a microservice node on the canvas by giving it a name. The microservice
exists on its own, with or without a spec. The team member links a spec card to a microservice
by an explicit action, for example dragging the card onto the microservice or choosing the
microservice from the card's menu. A linked card stays visually attached to its microservice
and moves with it. The team member can unlink the card again; the card stays on the canvas,
unlinked.

**Why this priority**: Microservices are what connections join. Without them the canvas has
nothing to connect, and without linking, specs cannot be traced to the services that own them.

**Independent Test**: Create microservices "Orders" and "Billing". Link an imported spec card to
"Orders", move "Orders" and confirm the card follows. Try to link a second card to "Orders"
and confirm it is refused. Unlink the card and confirm it remains on the canvas as an unlinked
card, and "Orders" remains as a microservice with no spec.

**Acceptance Scenarios**:

1. **Given** a canvas, **When** a team member creates a microservice with a name, **Then** a
   microservice node appears with that name and no spec.
2. **Given** an unlinked spec card and a microservice with no spec, **When** the team member
   links them, **Then** the card is shown attached to the microservice and both remain linked
   after a page reload.
3. **Given** a microservice already linked to a card, **When** the team member tries to link a
   different card to it, **Then** the action is refused with an explanation, or the team member
   is offered to replace the existing link; the existing link never changes silently.
4. **Given** a spec card already linked to one microservice, **When** the team member tries to
   link it to a second microservice, **Then** the action is refused with an explanation.
5. **Given** a linked card, **When** the team member unlinks it, **Then** the card stays on the
   canvas unlinked, the microservice stays with no spec, and no connection changes.
6. **Given** a microservice, **When** the team member renames it, **Then** its connections,
   link and position are kept.
7. **Given** a microservice with connections, **When** the team member deletes it, **Then**
   they are warned how many connections reference it, and after confirming, those connections
   are kept and flagged as dangling (see User Story 3).

---

### User Story 3 - Declare connections with a JSON document and on the canvas (Priority: P1)

The team describes how microservices talk to each other as **connections**. A connection is one named,
directed interaction from one microservice to another, for example "Order placed" from Orders
to Billing. Two microservices can have several connections between them, in either direction. The
connections are listed in a **connections document**, a JSON document. A team member uploads this
document, or opens it in the in-app editor and edits it there. Each connection appears on the
canvas as its own directional arrow labelled with its name. Team members can also draw a
connection by dragging from one microservice to another and naming it, and delete one from the
canvas. The
canvas and the document always describe the same connections: after drawing on the canvas,
the in-app editor and an export show the new connection, and re-importing an export gives back
exactly the same connections.

**Why this priority**: Declared connections are the core value of the new model: the
architecture the team actually agreed on, versionable outside the app.

**Independent Test**: Upload a document with three connections between four microservices, two of
them between the same pair, and confirm three separate, labelled arrows appear. Draw a fourth connection on the canvas, open the editor and
confirm it lists four. Export, delete everything, re-import the export, and confirm the same
four connections and microservices are back. Upload a document with a malformed entry and
confirm it is rejected with an error naming that entry.

**Acceptance Scenarios**:

1. **Given** a valid connections document, **When** a team member uploads it, **Then** they
   see a preview of what will change (connections and microservices added, removed or
   changed), and after confirming, the canvas shows one arrow per connection.
2. **Given** two microservices with three connections between them (two A→B, one B→A), **When**
   the canvas is shown, **Then** each connection is a separate arrow with its own name, and the
   arrows do not overlap.
3. **Given** a document that names a microservice not yet on the canvas, **When** it is
   applied, **Then** that microservice is created (with no spec), and the preview listed it
   as new.
4. **Given** a document with an error (not valid JSON, a missing name, source or target, a
   connection from a microservice to itself, or an unknown field), **When** a team member
   uploads or saves it, **Then** nothing is applied and every error is reported with the
   position of the offending entry (for example "connections[3].target is missing").
5. **Given** the canvas, **When** a team member draws a connection from microservice A to
   microservice B and gives it a name, **Then** a named arrow from A to B appears, even if
   other connections from A to B already exist, and the connections document now
   includes it.
6. **Given** a connection on the canvas, **When** a team member deletes it, **Then** only that
   arrow disappears and the connections document no longer includes it.
7. **Given** the in-app editor, **When** a team member edits the document and saves, **Then**
   the canvas reflects the saved document; **When** they discard instead, **Then** nothing
   changes.
8. **Given** any set of connections with none dangling, **When** a team member exports the document and later
   imports that export unchanged, **Then** the resulting connections and microservice names
   are identical and nothing is duplicated.
9. **Given** a connection whose microservice was deleted, **When** the canvas is shown,
   **Then** the connection is flagged as dangling in a review list, with the option to delete
   it or to recreate or reassign the missing microservice; it is never removed automatically.
10. **Given** two connections with the same name between the same source and target appear in a
    document, **When** it is uploaded, **Then** it is rejected as a duplicate entry.
11. **Given** a connection, **When** a team member selects it, **Then** they see its name, source,
    target, kind and description, and can rename it or change its kind or description.
12. **Given** a document with one `request` connection, one `event` connection and one connection without a
    kind, **When** it is applied, **Then** the three arrows are drawn solid, dashed and dotted
    respectively, and a legend explains the styles.

---

### User Story 4 - Search spec cards and highlight a microservice's neighbours (Priority: P2)

During a discussion someone asks "who deals with `customerId`?". A team member types the name
into search. Every spec card with a matching field or endpoint is highlighted, matching rows
are marked, and each card's linked microservice is highlighted with it. Unlinked cards that
match are highlighted too and shown as unlinked. Separately, clicking a microservice dims
everything except that microservice, its direct upstream and downstream connected
microservices, the connections between them, and their linked cards.

**Why this priority**: This is how the team gets answers from the canvas during a meeting. It
depends on User Stories 1–3 having put content on the canvas.

**Independent Test**: With four microservices, three linked cards and one unlinked card, search
for a field present in two linked cards and the unlinked card; confirm exactly those three
cards and the two linked microservices are highlighted. Click a microservice with one upstream
and one downstream connection and confirm only those three microservices and their two
connections stay at full emphasis.

**Acceptance Scenarios**:

1. **Given** cards on the canvas, **When** a team member searches for a field name, **Then**
   every card containing a matching field is highlighted, the matching fields are marked, and
   the result list shows each match with its card, endpoint and linked microservice (or
   "unlinked").
2. **Given** a search, **When** it matches an endpoint path or operation name, **Then** that
   card and endpoint are included in the results.
3. **Given** search results, **When** the team member picks a result, **Then** the canvas
   pans and zooms to that card and expands the matching endpoint.
4. **Given** a microservice with connections, **When** a team member clicks it, **Then** only
   it, its directly connected upstream and downstream microservices, the connections between
   them, and their linked cards remain at full emphasis; everything else is dimmed.
5. **Given** an active search or highlight, **When** the team member clears it, **Then** the
   canvas returns to normal emphasis.

---

### User Story 5 - Plan changes in Draft Scenario Mode (Priority: P2)

In a refinement meeting the team toggles **Draft Scenario Mode** and sketches a change:
proposed microservices, proposed links between cards and microservices, proposed connections,
and proposed endpoints or fields on cards. They can also mark existing connections, microservices
or spec links as proposed for removal, and propose changing an existing connection's name, kind or
description. Proposed elements look clearly different from
baseline ones. The baseline is never changed. The team can save the scenario, reopen it later,
and export it as a JSON document that can be re-imported, or as an image.

**Why this priority**: Planning "what if" changes was the original reason for the tool and
remains so, but it builds on the baseline created by User Stories 1–3.

**Independent Test**: Enter draft mode, add a proposed microservice, a proposed connection from
it to an existing microservice, a proposed link of an unlinked card, and a proposed field on a
card. Save the scenario, leave draft mode and confirm the baseline is unchanged. Export as
JSON and as an image, re-import the JSON into a new scenario and confirm it matches.

**Acceptance Scenarios**:

1. **Given** draft mode, **When** a team member adds a proposed microservice, a proposed
   connection or a proposed link, **Then** it appears with distinct "proposed" styling.
2. **Given** draft mode, **When** a team member marks a baseline connection or microservice as a
   proposed removal, or proposes a new kind for a baseline connection, **Then** the element is shown
   struck through and faded, or with its old and new kind, and leaving draft mode shows the
   baseline unchanged.
3. **Given** draft mode, **When** a team member adds a proposed endpoint or field to a card,
   including nested fields inside an object or array field, **Then** it appears on the card
   with proposed styling, and the card's baseline content is unchanged.
4. **Given** draft mode, **When** a team member deletes or changes anything, **Then** only the
   scenario changes; on leaving draft mode the baseline is exactly as before.
5. **Given** a scenario, **When** a team member saves it with a name, **Then** it can be listed
   and reopened later with all its proposed elements.
6. **Given** a scenario, **When** a team member exports it as JSON and imports that file,
   **Then** the imported scenario matches the original; **When** they export it as an image,
   **Then** the image shows the visible canvas including proposed styling.
7. **Given** a saved scenario that references a microservice or card that no longer exists in
   the baseline, **When** it is opened, **Then** the affected proposed elements are flagged as
   orphaned instead of being dropped.
8. **Given** a scenario, **When** a team member exports the connections document from draft
   mode, **Then** they choose whether to export the baseline only, or the baseline with the
   scenario applied (proposed connections added, proposed removals left out, proposed changes
   applied).

---

### User Story 6 - Migrate existing data from the previous version (Priority: P3)

A team that already used the previous version (feature 002) upgrades. Their data is migrated
once, automatically: each registered service becomes a microservice with the same name,
linked to a spec card built from its stored spec; connections that were drawn by hand or
confirmed become declared connections; inferred connections, dismissals and inference settings
are dropped. Positions and saved scenarios are kept. Afterwards the team sees a migration
summary.

**Why this priority**: It protects existing work, but only applies once and only to teams that
used the previous version.

**Independent Test**: Start from a database of the previous version with three services, one
confirmed edge, one manual edge, several inferred edges and two saved scenarios. Upgrade and
confirm three linked microservices, exactly two connections, the same positions, both
scenarios, and a summary reporting the counts and anything not carried over.

**Acceptance Scenarios**:

1. **Given** data from the previous version, **When** the new version starts for the first
   time, **Then** each service becomes a microservice of the same name linked to a spec card
   from its stored spec, at its previous position.
2. **Given** manual and confirmed edges, **When** migration runs, **Then** each becomes one
   declared connection between the corresponding microservices, and inferred edges, dismissals
   and inference settings are not carried over.
3. **Given** saved scenarios, **When** migration runs, **Then** they remain listed and
   openable, and any proposed element that cannot be expressed in the new model is listed in
   the migration summary instead of being silently dropped.
4. **Given** migration has run, **When** the application restarts, **Then** migration does not
   run again and nothing is duplicated.
5. **Given** migration has run, **When** a team member opens the migration summary, **Then**
   it shows how many microservices, cards, connections and scenarios were migrated, how many
   dismissals and deleted edges were discarded, and states that inferred edges (which were
   never stored) are no longer shown.

---

### Edge Cases

- A spec with no endpoints still becomes a card, showing that it has no endpoints.
- A very large spec (hundreds of endpoints) must keep the card readable: endpoints collapse by
  default and the card shows less detail when zoomed out.
- Two spec cards with the same title are allowed; they are told apart by their source and
  import time.
- Deleting a linked spec card leaves the microservice in place with no spec and does not
  change connections.
- Deleting an unlinked card has no effect on microservices or connections.
- Two microservices cannot share the same name (compared ignoring case, surrounding and
  repeated spaces), because the connections document refers to microservices by name.
- Renaming a microservice to a name used by another microservice is refused.
- Renaming a microservice updates the connections document, so an export uses the new name.
- A connection from a microservice to itself is rejected, both in the document and when drawn.
- Any number of connections between the same two microservices is allowed, in either direction.
  Connections from the same source to the same target MUST have different names; the same name may
  be reused between other pairs.
- When zoomed far out, many connections between the same pair may be drawn as one bundled arrow
  showing the number of connections; zooming in shows each connection separately.
- An uploaded connections document that is empty (no connections) removes all connections
  after the preview is confirmed; the preview states clearly how many connections will be
  removed.
- A connections document that is larger than the upload size limit or not valid JSON is
  rejected before any change.
- The in-app editor has unsaved changes when the team member closes it: they are asked to save
  or discard.
- A re-sync URL that becomes unreachable leaves the card as it was and reports the failure.
- Scenario proposed fields that referred to a card that was re-synced and no longer has the
  endpoint are flagged as orphaned.

## Requirements *(mandatory)*

### Functional Requirements

**Spec cards**

- **FR-001**: Users MUST be able to import an OpenAPI 2.0 or 3.x spec, in JSON or YAML, either
  by pasting its text or by giving a URL.
- **FR-002**: Each successful import MUST create one unlinked spec card on the canvas showing
  the spec title and version and every endpoint with its HTTP method and path.
- **FR-003**: Users MUST be able to expand an endpoint on a card to see its request inputs
  (path, query and header parameters and body fields) and response fields, including nested
  fields of objects and arrays.
- **FR-004**: Users MUST be able to re-sync a card imported by URL. Re-sync MUST update the
  same card in place and keep its position and its link.
- **FR-005**: Invalid specs and specs with unresolvable references MUST be rejected with a
  message naming the problem and its location in the spec. Nothing is created on failure.
- **FR-006**: Importing content identical to an existing card MUST NOT create a duplicate.
- **FR-007**: Importing, re-syncing or deleting a spec card MUST NOT create, change or delete
  any connection.
- **FR-008**: Users MUST be able to delete a spec card.
- **FR-009**: Each card MUST show where it came from (pasted, or its URL) and when it was last
  imported or re-synced.

**Microservices and links**

- **FR-010**: Users MUST be able to create, rename, describe and delete microservice nodes. A
  microservice MUST be able to exist without a spec card.
- **FR-011**: Microservice names MUST be unique, compared ignoring case, surrounding spaces,
  and repeated spaces inside the name ("Order  Service" equals "order service").
- **FR-012**: Users MUST be able to link an unlinked spec card to a microservice that has no
  card, and to unlink it again. A microservice MUST have at most one card, and a card MUST be
  linked to at most one microservice.
- **FR-013**: Linking a card to a microservice that already has one MUST be refused with an
  explanation unless the user explicitly chooses to replace the current card, which then
  becomes unlinked. Linking a card that is already linked to another microservice MUST be
  refused with an explanation; it must be unlinked first.
- **FR-014**: A linked card MUST be displayed as a separate box docked directly below its
  microservice and MUST move with it. Users MUST be able to collapse a docked card to a small
  "API: <title>" badge on the microservice and expand it again; the collapsed state is kept on
  reload. Unlinking undocks the card and places it next to the microservice.
- **FR-015**: Microservice nodes and spec cards MUST be visually distinct.
- **FR-016**: Deleting a microservice MUST warn how many connections reference it. After
  deletion its linked card MUST become unlinked, not deleted.

**Connections and the connections document**

- **FR-017**: A connection MUST run from one microservice (source) to a different
  microservice (target), MUST have a name, and MAY have a description and a kind. The kind,
  when given, MUST be either `request` (a synchronous call) or `event` (an asynchronous
  message). Any number of connections
  MAY exist between the same two microservices, in either direction; connections with the same
  source and target MUST have different names (compared like microservice names, FR-011).
- **FR-018**: Each connection MUST be displayed as its own directional arrow labelled with its name.
  Several connections between the same pair MUST be drawn so they do not overlap. When zoomed far
  out they MAY be bundled into one arrow showing the number of connections.
- **FR-018a**: Users MUST be able to select a connection to see its name, source, target, kind and
  description, and to rename it or change its kind or description.
- **FR-018b**: `request` connections MUST be drawn as solid arrows, `event` connections as dashed arrows,
  and connections without a kind as dotted neutral arrows. A legend MUST explain the styles. Proposed
  connections in Draft Scenario Mode keep their kind's line style but MUST also carry the distinct
  proposed color.
- **FR-019**: The system MUST NOT create connections automatically from spec content or by
  any other inference.
- **FR-020**: Users MUST be able to upload a connections document. Before it is applied, users
  MUST see a preview listing connections and microservices to be added, removed and changed,
  and MUST confirm it.
- **FR-021**: An applied connections document MUST replace the baseline connections entirely: it becomes
  the complete set of baseline connections, and connections missing from it are removed. The preview MUST
  list every connection that will be removed.
- **FR-021a**: The connections document MAY contain a list of microservices, each with a name and an
  optional description. Microservices listed there or named by a connection that do not exist MUST
  be created without a spec; listed descriptions MUST update existing microservices.
  Microservices not named in the document MUST NOT be deleted.
- **FR-022**: Users MUST be able to view and edit the current connections document in the app,
  then save or discard their changes. Saving MUST go through the same validation and preview
  as an upload.
- **FR-023**: The system MUST validate every uploaded or saved document and reject it as a
  whole when it has any error, listing each error with the position of the offending entry.
  Errors include invalid JSON, unsupported document version, missing or empty name, source
  or target, a kind other than `request` or `event`, a connection from a microservice to itself, duplicate connections, and unknown
  properties.
- **FR-024**: Users MUST be able to draw a connection between two microservices on the canvas,
  giving it a name, including between microservices that already have connections, and to delete a
  single connection from the canvas. Both MUST be reflected in the connections
  document immediately.
- **FR-025**: Users MUST be able to export the current connections document as a file. The export
  MUST list every microservice (with its description) as well as every attached connection.
  Dangling connections cannot be expressed in the document and are not exported: while any exist,
  the export MUST warn how many were left out, and re-importing such an export lists them under
  "removed" in the preview, so they are only removed if the user confirms.
  Importing an unchanged export MUST give the same connections and microservice names, with no
  duplicates.
- **FR-026**: Connections MUST be stored independently of spec cards. Re-syncing, unlinking or
  deleting a card MUST NOT affect them.
- **FR-027**: A connection whose source or target microservice no longer exists MUST be
  flagged as dangling and shown in a review list. Users MUST be able to delete it, or recreate
  or reassign the missing microservice. The system MUST NOT delete it automatically.
- **FR-028**: Renaming a microservice MUST keep its connections, and the connections document
  MUST use the new name from then on.
- **FR-029**: The connections document format MUST be documented for users, including a
  version identifier and an example, so teams can maintain it outside the app.

**Search and highlighting**

- **FR-030**: Users MUST be able to search for a text across field names, endpoint paths and
  operation names of all spec cards, matching case-insensitively and partially.
- **FR-031**: Search results MUST list each match with its card, endpoint and linked
  microservice (or "unlinked"), and highlight the matching cards and their linked microservices
  on the canvas.
- **FR-032**: Selecting a search result MUST focus the canvas on that card and expand the
  matching endpoint.
- **FR-033**: Clicking a microservice MUST highlight it, its directly connected upstream and
  downstream microservices, the connections between them, and their linked cards, and dim
  everything else. Users MUST be able to clear the highlight.

**Layout**

- **FR-034**: Users MUST be able to run an automatic hierarchical layout of microservices and
  cards on demand, keeping linked cards attached to their microservices.
- **FR-035**: Positions dragged by users MUST be saved and MUST take precedence over automatic
  layout, which MUST run only when a user asks for it.

**Draft Scenario Mode**

- **FR-036**: Users MUST be able to enter and leave Draft Scenario Mode.
- **FR-037**: In draft mode users MUST be able to add proposed microservices, proposed links
  between cards and microservices, proposed connections, proposed endpoints on cards, and
  proposed fields (including nested fields) on card endpoints, and to remove proposed elements.
- **FR-037b**: Proposed microservices MUST stay editable in the draft: their name (unique, FR-011) and
  description can be changed, and the proposal removed, from the microservice's menu and the Proposals panel.
- **FR-037a**: In draft mode users MUST be able to mark baseline connections, microservices and spec
  links as "proposed removal", and to propose changes to a baseline connection's name, kind or
  description. These proposals MUST be stored only in the scenario, MUST be shown with their
  own styling (proposed removals struck through and faded red, proposed changes showing the
  old and new values), and MUST be reversible within the scenario. Proposing the removal of a
  microservice MUST also show its connections and spec link as proposed removals. Baseline endpoints
  and fields MUST NOT be proposed for removal or change.
- **FR-038**: Proposed elements MUST be visually distinct from baseline elements.
- **FR-039**: Nothing done in draft mode MUST change the baseline (cards, microservices, links,
  connections, layout or the baseline connections document).
- **FR-040**: Users MUST be able to save, list, reopen, rename and delete named scenarios.
- **FR-041**: Users MUST be able to export a scenario as a re-importable JSON document and the
  visible canvas as an image.
- **FR-042**: Proposed elements that reference baseline elements that no longer exist MUST be
  flagged as orphaned when the scenario is opened, not dropped.
- **FR-043**: In draft mode, exporting the connections document MUST let users choose baseline
  only, or the baseline with the scenario applied (proposed connections added, proposed removals
  left out, proposed changes applied).

**Migration**

- **FR-044**: On first start after upgrading, the system MUST migrate data from the previous
  version once: each service becomes a microservice of the same name linked to a spec card
  from its stored spec, at its previous position; each manual or confirmed edge becomes one
  declared connection, named after the edge's label or, when it has none, "<source> → <target>";
  inferred edges, dismissals and inference settings are discarded; saved
  scenarios are kept.
- **FR-045**: Scenario elements that cannot be expressed in the new model MUST be reported in
  a migration summary, not silently dropped.
- **FR-046**: Migration MUST run at most once and MUST NOT duplicate data on restart.
- **FR-047**: Users MUST be able to see a migration summary with counts of migrated and
  discarded items.

**Removal of inference**

- **FR-048**: Inference-related features of the previous version MUST be removed from the
  user interface: confidence display, confirming or dismissing inferred connections, and
  inference settings.

**Flows (colors)**

- **FR-049**: Users MUST be able to create, rename, recolor and delete flows. A flow has a
  name (unique, compared like microservice names, FR-011) and a color from a fixed palette of
  eight colors that excludes green and red, which are reserved for proposed and removed elements
  (FR-038).
- **FR-050**: A connection MAY belong to one flow, chosen when drawing or editing it. A grouped connection is
  drawn in its flow's color, keeping its kind's line style (FR-018b). Deleting a flow keeps its
  connections, ungrouped.
  A *proposed* connection in a flow keeps the flow's color too, and is marked as proposed by a green halo
  along its line (Principle IV); a proposed connection without a flow is green, and proposed removals are red.
- **FR-051**: The legend MUST list every flow with its color. Selecting a flow MUST highlight its
  connections and the microservices they connect and dim everything else, like FR-033.
- **FR-052**: The connections document MAY contain a `flows` list (name and color), and each connection MAY name
  its `flow`. A connection naming a flow that the document does not list is an error. Applying a
  document creates missing flows and updates listed colors; flows not in the document are never
  deleted. Export lists every flow and each connection's flow, and the round trip of FR-025 includes them.
- **FR-053**: In Draft Scenario Mode, proposed connections MAY name an existing flow, and a proposed connection
  change MAY change a baseline connection's flow. Flows themselves are baseline data and are edited only
  outside draft mode.

**Connection endpoint (API)**

- **FR-054**: A connection MAY name one endpoint of its target microservice's API, as `METHOD /path` (for
  example `POST /invoices`). When drawing or editing a connection, users MUST be able to pick it from the
  endpoints of the target's spec card (including proposed endpoints in draft mode), or leave it empty.
- **FR-055**: The endpoint MUST be shown explicitly on the diagram, as a badge near the connection's
  arrowhead with the method and path (method only when zoomed far out), separate from its name label. If the target has a spec card and the
  endpoint is not in it (for example after a re-sync or a new link), the connection MUST be flagged as
  "endpoint not in spec" and kept unchanged (path templates compare like endpoint keys, so
  `/orders/{id}` matches `/orders/{orderId}`). A target without a spec is not flagged.
- **FR-056**: The connections document MAY give each connection an `endpoint` string; a value that is not
  `METHOD /path` with a valid HTTP method is an error. Changing a connection's endpoint is a connection change in the
  preview, and the export round trip of FR-025 includes it. Draft scenarios MAY set the endpoint of
  proposed connections and propose changing a baseline connection's endpoint.

### Key Entities *(include if feature involves data)*

- **Spec Card**: A parsed API spec. Has a title, version, source (pasted text or URL), time of
  last import, and its endpoints with their request and response fields. Linked to at most one
  microservice. Has a canvas position.
- **Endpoint / Field**: An operation of a spec card (method and path, optional operation name
  and summary) and its request and response fields, including nested ones.
- **Microservice**: A named node on the canvas, unique by name, with an optional description.
  Has a canvas position and at most one linked spec card.
- **Connection**: A named, directed interaction from a source microservice to a different target
  microservice, with an optional description and an optional kind (`request` or `event`).
  Several connections can exist between the same pair;
  the name is unique per source and target. May be flagged as dangling.
- **Connection Flow**: A named color (from a fixed palette) that connections can belong to, so a business
  process spanning several connections is easy to follow.
- **Connections Document**: The JSON representation of all baseline connections and microservices (names
  and descriptions), with a format version. Can be uploaded, edited in the app and exported; it always matches the
  connections on the canvas.
- **Scenario**: A named draft containing proposed microservices, links, connections,
  endpoints, fields and positions, proposed removals of baseline connections, microservices and
  links, and proposed changes to baseline connections, stored separately from the baseline.
- **Migration Summary**: A record of the one-time migration from the previous version, with
  counts of migrated and discarded items and any elements that could not be carried over.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can import a spec by paste or URL and see its card in under
  30 seconds, for specs of up to 200 endpoints.
- **SC-002**: A team member can create a microservice and link a card to it in under
  15 seconds.
- **SC-003**: When no connections are dangling, exporting the connections document and re-importing it
  unchanged produces exactly the same connections in 100% of cases, with zero duplicates (the
  preview reports no changes).
- **SC-004**: 100% of invalid connections documents are rejected without any partial change,
  and every error names the offending entry.
- **SC-005**: A connections document with 200 connections between 100 microservices is
  validated, previewed and applied in under 5 seconds, and the canvas stays responsive while
  panning and zooming.
- **SC-006**: Search results for a field name appear within 1 second on a canvas with 100
  spec cards.
- **SC-007**: Zero connections are created by importing or re-syncing specs.
- **SC-008**: After migration, 100% of previously manual or confirmed edges exist as declared
  connections and 100% of previously registered services exist as linked microservices.
- **SC-009**: Leaving Draft Scenario Mode leaves the baseline identical to before entering it,
  in 100% of cases.
- **SC-010**: In a refinement meeting, a team can sketch a proposed microservice with two
  connections and export it as an image in under 2 minutes.

## Assumptions

- Terminology: the "connections document" is what constitution v2.0.0 calls the connections
  document, and a "connection" is a connection (edge) between two microservices.
- As in the previous version, one editor shares their screen during meetings; concurrent
  multi-user editing is out of scope for this version.
- No authentication or per-user permissions are added; the tool runs inside a trusted team
  environment.
- Microservices are identified by their name in the connections document; that is why names
  are unique.
- A connections document describes connections and microservices (names and descriptions). Microservice
  positions, links to spec cards, and spec content are not part of it; they are managed on
  the canvas.
- Applying a connections document replaces the baseline connections (it is the complete list),
  but never deletes microservices; removing a microservice is always a canvas action.
- Connections are microservice to microservice. A connection may name one endpoint of its target (FR-054), but
  never fields.
- Spec import keeps the previous version's limits and protections: an upload size limit, and
  restricted fetching of remote references and URLs.
- Proposed fields and endpoints in Draft Scenario Mode behave as in the previous version,
  including nested fields inside object and array fields.
- The migration targets the previous version's data store only; importing data from other
  tools is out of scope.
