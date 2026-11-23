# Quickstart: Meeting Tools

Prerequisites and startup as in `specs/003-declared-connections/quickstart.md` (backend on :8080, Vite on :5173).

1. **Walkthrough** — put three connections in flow "Checkout", set steps 1, 2, 2 (details panel → Step), open
   the legend, click ▶ next to "Checkout": step 1 is emphasized; press → : both step-2 connections are; Esc ends.
   Reorder in the Flows panel (▲/▼) and walk again.
2. **Undo/redo** — delete a microservice with a card and connections; press Ctrl+Z: it comes back with its
   card, position, team and connections; Ctrl+Shift+Z deletes it again. Drag a node, Ctrl+Z: it moves back.
3. **Ownership** — ⋯ → Edit on a microservice: set team "Payments", repository and docs URLs; the node shows
   the team and links; pick "Payments" in the Team filter: other microservices dim. Export the connections
   document: the microservice entry has `team`, `repository`, `docs`.
4. **Notes** — toolbar **+ Note** → choose Question, type text, drag it; reload: it is still there. Enter a
   draft, add a Decision note, leave the draft: only the question remains.

Automated: `./mvnw verify` (backend), `npm run typecheck && npm run lint && npx vitest run && npx playwright test` (frontend).
