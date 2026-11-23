# Feature Specification: Meeting Tools — Flow Walkthroughs, Undo/Redo, Ownership, Notes

**Feature Branch**: `004-meeting-tools`

**Created**: 2026-11-22

**Status**: Draft

**Builds on**: `specs/003-declared-connections` (microservices, spec cards, connections, flows, drafts)

**Input**: User description: "do 1 then Undo/redo then ownership details on microservices then Notes on the
canvas" — from the suggestions: (1) ordered flow walkthroughs: number the connections of a flow as steps and
walk through them one step at a time in a meeting; undo/redo for canvas edits; ownership details on
microservices (team, repository and docs links) with a team filter; sticky notes on the canvas to capture
decisions and open questions.

## Clarifications

### Session 2026-11-22

Defaults chosen without a question round (recorded so they can be revisited):

- Steps are numbers on connections within their flow; **several connections may share a step number**,
  meaning they happen in parallel (for example an event fanning out). A connection without a step comes after
  the numbered ones, in name order.
- Undo/redo covers the edits made in this browser session, not changes made elsewhere; it is lost on reload.
  Re-importing, re-syncing and deleting spec cards are not undoable and say so when confirmed.
- Ownership is a team name plus optional repository and documentation links (http or https).
- Notes are free-standing sticky notes of three kinds: note, decision, question. They are not part of the
  connections document. Notes added in Draft Scenario Mode belong to the scenario.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Walk through a flow step by step (Priority: P1)

A flow such as "Checkout" runs through several connections. The team numbers its connections as steps
(1, 2, 3…), and the numbers appear on the arrows in the flow's color. In the meeting, the presenter starts a
walkthrough of the flow: the canvas focuses on step 1's connection(s) and their microservices, dims the rest,
and Next/Previous (or the arrow keys) move through the steps while a bar shows "Checkout · step 2 of 5" and
what happens at that step.

**Why this priority**: Flows were defined as business processes; walking through them in order is how a
process is explained and refined in a meeting.

**Independent Test**: Put four connections in "Checkout" with steps 1, 2, 2 and 3; start the walkthrough;
step 2 highlights both of its connections; Next past the last step stops; Esc ends the walkthrough.

**Acceptance Scenarios**:

1. **Given** a connection in a flow, **When** a step number is set (or left blank to append), **Then** a
   numbered badge in the flow's color appears on its arrow.
2. **Given** a flow with steps, **When** the presenter starts its walkthrough, **Then** only the current
   step's connections and microservices are emphasized, earlier steps stay visible but quiet, later ones are
   dimmed, and the canvas pans to the current step.
3. **Given** a walkthrough, **When** Next, Previous, ←, → or Esc is used, **Then** it moves between steps or
   ends; the bar shows the step number, total and each current connection with its endpoint.
4. **Given** the Flows panel, **When** the team reorders a flow's connections, **Then** the step numbers update
   and the walkthrough follows the new order.
5. **Given** the connections document, **When** a connection has `"step": 2`, **Then** it imports and exports
   with that step; a step without a flow, or not a positive whole number, is an error with its position.
6. **Given** Draft Scenario Mode, **When** a proposed connection in a flow gets a step or a baseline
   connection's step is proposed to change, **Then** the walkthrough in the draft uses the proposed order.

---

### User Story 2 - Undo and redo canvas edits (Priority: P2)

A team member drags, deletes or renames something by mistake during a meeting and presses Ctrl+Z to undo it,
or Ctrl+Shift+Z / Ctrl+Y to redo it. Toolbar buttons do the same and say what they will undo.

**Why this priority**: Mistakes in front of an audience are costly; undo makes the tool safe to drive live.

**Independent Test**: Delete a microservice that has a docked card and two connections; undo; it is back with
its name, description, team, position, card and connections; redo deletes it again.

**Acceptance Scenarios**:

1. **Given** any of these edits — moving nodes, auto-arrange, collapsing a card, creating/renaming/describing/
   deleting a microservice, linking/unlinking a card, creating/editing/deleting/reassigning a connection,
   creating/editing/deleting a flow, reordering steps, applying a connections document, and note edits —
   **When** the user undoes it, **Then** the canvas returns to the state before it; redo re-applies it.
2. **Given** a deleted microservice, **When** it is undone, **Then** it is recreated with the same name,
   description, ownership, position and card link, and its connections re-attach.
3. **Given** Draft Scenario Mode, **When** the user undoes, **Then** only draft edits are undone, never the
   baseline; leaving or entering draft mode starts a fresh history.
4. **Given** an edit that cannot be undone (importing, re-syncing or deleting a spec card), **When** it is
   confirmed, **Then** the dialog says so.
5. **Given** an undo that fails because the data changed meanwhile (for example a name now taken), **Then** the
   user is told and the history stays consistent.

---

### User Story 3 - Ownership details on microservices (Priority: P3)

Each microservice can record its owning team and links to its repository and documentation. The team shows on
the node, the links open in a new tab, and a team filter highlights one team's microservices.

**Independent Test**: Set team "Payments" and both links on Billing; the node shows the team and two links;
filtering by "Payments" dims the others; the connections document round-trips the three fields.

**Acceptance Scenarios**:

1. **Given** a microservice, **When** team, repository URL and documentation URL are edited, **Then** they are
   saved, shown on the node and the links open in a new tab; URLs must be http or https.
2. **Given** several teams, **When** a team is chosen in the team filter, **Then** its microservices, their
   cards and the connections between them stay emphasized and everything else is dimmed.
3. **Given** the connections document, **When** a microservice entry has `team`, `repository` and `docs`,
   **Then** they apply like `description` (absent = unchanged, null = cleared) and export back.
4. **Given** search in name mode, **When** a team name is searched, **Then** that team's microservices match.
5. **Given** Draft Scenario Mode, **When** a proposed microservice is created or edited, **Then** a team can be
   set on it.

---

### User Story 4 - Notes on the canvas (Priority: P4)

During a meeting someone adds a sticky note — a plain note, a decision or an open question — types it, and
places it next to what it is about. Notes are saved with the architecture, appear in image exports, and can
be edited, moved or deleted.

**Independent Test**: Add a question note, type text, move it, reload: it is still there; in a draft, add a
decision note; leave the draft: the baseline has only the question.

**Acceptance Scenarios**:

1. **Given** the canvas, **When** a note is added, **Then** it appears in view, editable, with its kind shown
   by an icon and color; text up to 2000 characters.
2. **Given** a note, **When** it is edited, moved, changed to another kind or deleted, **Then** the change is
   saved (and undoable).
3. **Given** Draft Scenario Mode, **When** notes are added there, **Then** they are stored in the scenario,
   shown as proposed, and baseline notes are read-only in the draft.
4. **Given** an image export, **Then** notes are included.

### Edge Cases

- Deleting a flow clears the steps of its connections; moving a connection to another flow appends it there
  unless a step is given.
- A walkthrough of a flow whose connections have no steps follows name order; a flow with no connections
  cannot be walked through.
- A walkthrough ends if its flow is deleted; it skips connections removed meanwhile.
- Undo after the page reloads is not available (history is per session).
- A redo stack is cleared by a new edit.
- Team names compare like microservice names when filtering.
- Empty notes are allowed while typing but are not saved until they have text.

## Requirements *(mandatory)*

### Functional Requirements

**Walkthroughs**

- **FR-001**: A connection in a flow MAY have a step: a whole number ≥ 1. Connections with the same step in a
  flow happen in parallel. A connection without a flow has no step.
- **FR-002**: When a connection joins a flow without a step, it MUST get the next step after the flow's
  highest. Deleting a flow or removing a connection from it MUST clear the step.
- **FR-003**: Steps MUST show on the diagram as numbered badges in the flow's color on the connection.
- **FR-004**: Users MUST be able to set a step when drawing or editing a connection and to reorder a flow's
  connections in the Flows panel; reordering renumbers the steps 1..n (keeping parallel groups together).
- **FR-005**: Users MUST be able to walk through a flow: start, next, previous and end (buttons and ←, →, Esc),
  with the current step's connections and microservices emphasized, earlier steps quiet, later steps dimmed,
  the view panned to the current step, and a bar naming the flow, step, total, and each current connection
  with its source, target and endpoint.
- **FR-006**: The connections document MAY give a connection a `step`; a step on a connection without `flow`, or
  a value that is not a whole number ≥ 1, MUST be rejected with its position. Steps round-trip.
- **FR-007**: In Draft Scenario Mode, proposed connections MAY have a step and a step change MAY be proposed
  for a baseline connection; the draft's walkthrough uses them.

**Undo/redo**

- **FR-008**: Users MUST be able to undo and redo, per browser session, the edits listed in User Story 2
  scenario 1, with Ctrl+Z / ⌘Z, Ctrl+Shift+Z / Ctrl+Y and toolbar buttons labelled with the edit to undo/redo.
- **FR-009**: Undoing a deletion MUST restore the deleted item with its attributes; objects recreated by an
  undo get new identities, and later undo/redo steps MUST follow them.
- **FR-010**: In Draft Scenario Mode undo/redo MUST act on the draft only; switching modes starts a new history.
- **FR-011**: Importing, re-syncing and deleting spec cards are not undoable; their confirmations MUST say so.
- **FR-012**: A failed undo or redo MUST be reported and MUST NOT leave the history inconsistent. History is
  limited to the last 50 edits.

**Ownership**

- **FR-013**: A microservice MAY have a team (up to 120 characters), a repository URL and a documentation URL
  (http or https, up to 2048 characters), editable with its name and description.
- **FR-014**: The team MUST show on the microservice node and the links MUST open in a new tab.
- **FR-015**: A team filter MUST list the teams in use and emphasize the chosen team's microservices, their
  cards and the connections between them; name search MUST also match team names.
- **FR-016**: The connections document MAY give microservices `team`, `repository` and `docs`, with the same
  absent/null rules as `description`; invalid URLs MUST be rejected with their position; the fields round-trip.
- **FR-017**: Proposed microservices MAY have a team.

**Notes**

- **FR-018**: Users MUST be able to add, edit, move, change the kind of, and delete notes on the canvas. A note
  has text (1–2000 characters), a kind (note, decision, question) and a position.
- **FR-019**: Notes MUST be saved with the baseline and shown after reload; they are not part of the connections
  document.
- **FR-020**: Notes added in Draft Scenario Mode MUST be stored in the scenario, shown as proposed and exported
  with it; baseline notes MUST be read-only in draft mode.
- **FR-021**: Notes MUST be included in image exports.

### Key Entities

- **Connection step**: a whole number on a connection, ordering it within its flow.
- **Microservice ownership**: team, repository URL, documentation URL.
- **Note**: text, kind (note, decision, question), position; baseline or scenario.
- **Undo history**: per-session stack of edits with their inverse.

## Success Criteria *(mandatory)*

- **SC-001**: A presenter can walk through a 10-step flow using only the keyboard, each step change taking
  under 1 second to show.
- **SC-002**: Every edit listed in FR-008 can be undone and redone, and undoing a microservice deletion
  restores its name, description, ownership, position, card link and connections in 100% of cases.
- **SC-003**: The connections document round-trips steps and ownership fields with no changes reported.
- **SC-004**: A note can be added and typed in under 5 seconds and survives a reload.

## Assumptions

- One editor at a time, as in 003; undo does not merge others' edits.
- Notes have no author or timestamps shown; they are meeting aids, not a discussion system.
- Team filtering and walkthrough highlighting are view state only (not saved).
