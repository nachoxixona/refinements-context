# Quickstart & Validation Guide: Declared Connections & Spec Cards

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **API**: [contracts/openapi.yaml](./contracts/openapi.yaml) | **Connections document**: [contracts/connections-document.schema.json](./contracts/connections-document.schema.json) | **Data**: [data-model.md](./data-model.md)

This guide shows how to run the app and prove each user story works end to end. It does not
contain implementation code; see `tasks.md`, which `/speckit-tasks` creates.

## Prerequisites

Same as [002](../002-architecture-visualizer/quickstart.md#prerequisites): JDK 21, Node.js 22
LTS and npm, and Docker for the Testcontainers tests.

## Before the first start: back up 002 data

The first start runs the one-time migration from feature 002 (research R10). It renames the
old tables instead of deleting them, but take a copy anyway:

```bash
cp -r backend/data backend/data.backup-002   # with the backend stopped
```

## Run locally

```bash
cd backend && ./mvnw spring-boot:run                       # :8080, dev profile, H2 file DB
cd frontend && npm install && npm run gen:api && npm run dev  # :5173, proxies /api
```

Open http://localhost:5173.

## Automated checks (merge gates)

```bash
cd backend  && ./mvnw verify       # unit, parsing, connections-document, migration, Testcontainers ITs, Spotless
cd frontend && npm run typecheck   # tsc --noEmit, strict
cd frontend && npm test            # Vitest
cd frontend && npm run e2e         # Playwright, on its own backend (:8081, in-memory H2) and Vite (:5174)
```

All checks should pass. `ConnectionsDocumentIT` asserts the export → re-import round trip
(SC-003) and that 200 connections between 100 microservices are applied in under 5 s (SC-005).
`Migration002IT` asserts SC-008.

## Sample data

- **Specs**: the 002 fixtures in `backend/src/test/resources/specs/reference-workspace/`. To
  serve them by URL:

  ```bash
  cp -r backend/src/test/resources/specs/reference-workspace /tmp/viz-specs
  cd /tmp/viz-specs && python3 -m http.server 9000
  ```

- **Connections documents** live in `backend/src/test/resources/connections/`:
  - `shop.json`: 5 microservices, 7 connections, including two Orders → Billing connections and a
    Billing → Orders event;
  - `invalid-mixed.json`: one error of each kind;
  - `large-200.json`: 200 connections between 100 microservices.

---

## Validation scenarios

### V1: Spec cards (User Story 1)

1. Click **Import spec** and paste `order-service.yaml`.
   - **Expect**: an unlinked card titled "Order Service" with method badges. Expanding an
     endpoint shows its fields. No arrows appear.
2. Import `http://localhost:9000/customer-service.json` by URL.
   - **Expect**: a second card that shows its source URL and import time.
3. Paste `order-service.yaml` again.
   - **Expect**: "This spec is already on the canvas" with a link to the existing card. No
     duplicate is created.
4. Import `broken.yaml`.
   - **Expect**: an error naming the missing `$ref` and its location. Nothing is added.
5. Edit `/tmp/viz-specs/customer-service.json` to add an endpoint, then click **Re-sync** on
   that card.
   - **Expect**: the same card, in the same place, shows the new endpoint.
6. With connections present (after V3), re-sync and delete a card.
   - **Expect**: the connection count in the toolbar is unchanged (FR-007).

### V2: Microservices and links (User Story 2)

1. Click **New microservice** and name it "Orders"; then create "Billing".
   - **Expect**: two microservice nodes, visibly different from cards.
2. Drag the Order Service card onto "Orders".
   - **Expect**: the card docks below "Orders". Dragging "Orders" moves both.
3. Collapse the docked card.
   - **Expect**: an "API: Order Service" badge replaces it. Reload the page: it is still
     collapsed. Expand it again.
4. Drag the Customer Service card onto "Orders".
   - **Expect**: "Orders already has a spec. Replace it?" Choose **Cancel**; nothing changes.
5. Create "orders " (lowercase, with a trailing space).
   - **Expect**: it is refused as a duplicate name.
6. Unlink the card from "Orders".
   - **Expect**: the card stays on the canvas next to "Orders", unlinked. "Orders" now has no
     spec.

### V3: Connections and the connections document (User Story 3)

1. Click **Import connections** and choose `shop.json`.
   - **Expect**: a preview listing 7 connections and the new microservices to add. Confirm.
   - **Expect**: 7 labelled arrows. The two Orders → Billing connections and the Billing → Orders
     connection are drawn apart. Request connections are solid, event connections dashed, and connections without a
     kind dotted. The legend explains the styles.
2. Zoom far out.
   - **Expect**: the Orders ↔ Billing connections become one arrow labelled "×3". Zoom back in and
     they separate again.
3. Drag from "Payments" to "Orders" and enter the name "Refund issued" with kind **event**.
   - **Expect**: a new dashed arrow. Open **Edit connections**: the document lists it.
4. In the editor, change one connection's `target` to `""` and click **Save**.
   - **Expect**: an inline marker on that line: "connections[2].target is missing". Nothing changes
     on the canvas. Click **Discard**.
5. Upload `invalid-mixed.json`.
   - **Expect**: every error is listed with its position. Nothing is applied.
6. With no dangling connections, click **Export connections**, then re-import the downloaded file.
   - **Expect**: the preview says **No changes** (SC-003). If connections are dangling, the export
     warns how many were left out, and a re-import preview lists them under Removed.
7. In the editor, delete one connection entry and save.
   - **Expect**: the preview lists exactly that connection under "Removed". Confirm; only that
     arrow disappears.
8. Delete the "Billing" microservice.
   - **Expect**: a warning that says how many connections reference it. After confirming, those connections
     appear under **Dangling connections** and are not deleted.
   - Create "Billing" again: the connections re-attach. Delete it once more, and use **Reassign**
     on one connection and **Delete** on another.
9. Select a connection.
   - **Expect**: its name, source, target, kind and description. Rename it; the label and
     the editor document both update.

### V4: Search and highlight (User Story 4)

1. Search for `customerId`.
   - **Expect**: matching cards are highlighted, and each result shows its linked microservice
     or "unlinked". Pick a result: the canvas zooms to that card and expands the endpoint.
2. Click "Orders".
   - **Expect**: only "Orders", its directly connected microservices, the connections between them
     and their docked cards stay at full emphasis. Press **Esc** to clear.

### V5: Draft Scenario Mode (User Story 5)

1. Enter **Draft Scenario Mode**. Add a proposed microservice "Loyalty" and a proposed connection
   "Points earned" (event) from "Orders" to "Loyalty".
   - **Expect**: green proposed styling. The proposed connection keeps the dashed event line.
2. Mark the "Create invoice" connection as **proposed removal**, and change another connection's kind
   from event to request.
   - **Expect**: the first is struck through and faded red. The second shows "event →
     request".
3. Propose linking the unlinked Customer Service card to "Loyalty", then add a nested field
   `shipping.city` to an endpoint on a card.
4. Save the scenario as "Loyalty v1" and leave draft mode.
   - **Expect**: the baseline is exactly as before (SC-009).
5. Reopen "Loyalty v1" and export it as JSON and as PNG. Import the JSON as a new scenario.
   - **Expect**: it matches the original.
6. In draft mode, choose **Export connections → with scenario applied**.
   - **Expect**: the file includes "Points earned" and "Loyalty", leaves out "Create invoice",
     and has the changed kind.
7. Import a scenario JSON exported by feature 002.
   - **Expect**: it imports, with conversion notes.

### V6: Migration from 002 (User Story 6)

1. Start the new version on the backed-up 002 database.
   - **Expect**: on first load, the **Migration summary** shows the counts: microservices,
     cards, connections (confirmed plus manual), scenarios, and discarded dismissals.
2. Check that each former service is a microservice with its card docked, at its former
   position, and that each former manual or confirmed edge is a connection.
3. Restart the backend.
   - **Expect**: no second migration, no duplicates, and the summary is not shown again
     (FR-046).

### V7: Performance smoke (SC-001, SC-005, SC-006)

1. Import `specs/oas30/large-200-endpoints.yaml`.
   - **Expect**: the card appears in under 30 s (SC-001; also asserted by `PerformanceSmokeIT`).
2. Import `large-200.json`.
   - **Expect**: preview and apply finish in under 5 s, and panning stays smooth.
3. Search across 100 cards.
   - **Expect**: results in under 1 s. Search runs in the browser, so this is covered by the
     Vitest timing check in `fieldSearch.test.ts` (T062), not by a backend test.
