# Research: Meeting Tools

## R1. Steps on connections

**Decision**: a nullable integer column `connection.step` (≥ 1). Rules live in `ConnectionService`: joining a
flow without a step appends (max + 1); leaving a flow clears the step; deleting a flow clears its connections'
steps. Reordering is one atomic call, `PUT /api/flows/{flowId}/steps` with `{steps: [{connectionId, step}]}`
covering exactly the flow's connections, so undo of a reorder is a single call with the previous steps.
**Alternatives**: an ordered list on the flow (harder to express parallel steps and the document's per-
connection `step`); fractional ranks (no user-visible meaning).

## R2. Walkthrough

**Decision**: view state only (`canvasStore.walkthrough = {flowId, index}`), computed from the current view
(baseline, or baseline + overlay in drafts), so proposed steps work with no extra code paths. Steps are the
distinct step values in ascending order; unnumbered connections follow, one step each in name order.
Highlight: current = active, earlier = quiet (`hl-visited`), later = dimmed. `fitView` on the current step's
nodes.

## R3. Undo/redo

**Decision**: a frontend command history (`historyStore`): each entry has a label and async `undo`/`redo`
functions. Entries are recorded centrally in the mutation hooks of `api/queries.ts`, which read the state
before the change from the canvas query cache, so callers don't change. Recreated objects get new ids; an
id remap table (`old → new`, followed transitively) is consulted by every entry when it runs. Layout edits
(moves, auto-arrange, collapse) are recorded by the canvas through `canvasStore.actions.applyLayout`. In
draft mode the history records draft snapshots instead (the draft is local state). Limit 50 entries; a new
edit clears redo; a failed undo/redo shows an error and leaves the entry where it was.
**Not undoable**: card import, re-sync, card deletion (spec content can't be recreated faithfully).
**Undo of document apply**: re-apply the previous exported document, then delete microservices the apply
created.
**Alternatives**: server-side event log (much larger change; history is per session by design).

## R4. Ownership

**Decision**: three nullable columns; URLs validated as absolute `http`/`https` URIs with a host, ≤ 2048
chars; team ≤ 120 chars, cleaned like names. The team filter is view state (`canvasStore.teamFilter`,
highlight mode `team`); teams compare with `normalizeLabel`.

## R5. Notes

**Decision**: table `note (id, text, kind, x, y, created_at, updated_at)`, `/api/notes` CRUD, included in
`GET /api/canvas` as `notes`. On the canvas a note is a React Flow node `note:{id}` of type `note`; its
position is saved on the note (PATCH) rather than in the baseline layout. Draft notes are
`ScenarioContent.notes` items with `tempKey` `proposed-note:{n}`. Image export already renders all nodes.
Notes don't bump the workspace revision (they are not part of the connections document).

## R6. Document and scenario compatibility

**Decision**: additive optional properties; the connections document stays `formatVersion` 2 and scenarios
stay `formatVersion` 3, so existing files keep importing unchanged. New properties: `connections[].step`,
`microservices[].team|repository|docs`; scenario `proposedConnections[].step`, `connectionChanges[].step`,
`proposedMicroservices[].team`, `notes`.
