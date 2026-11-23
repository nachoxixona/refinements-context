# Quickstart & Validation Guide: Architecture Visualizer

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **API**: [contracts/openapi.yaml](./contracts/openapi.yaml) | **Data**: [data-model.md](./data-model.md)

This guide shows how to run the app locally and prove each user story works end to end. It does
not contain implementation code; see `tasks.md` (created by `/speckit-tasks`).

## Prerequisites

- JDK 21
- Node.js 22 LTS and npm
- Docker (only for the Testcontainers integration tests and the optional PostgreSQL profile)

## Run locally

```bash
# Backend: dev profile, H2 file database, port 8080
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend: Vite dev server on port 5173, proxies /api to :8080
cd frontend
npm install
npm run gen:api   # regenerate TS types from contracts/openapi.yaml
npm run dev
```

Open http://localhost:5173. The backend's own API docs are at http://localhost:8080/swagger-ui.html.

Optional production-like run against PostgreSQL:

```bash
docker run -d --name viz-pg -e POSTGRES_PASSWORD=viz -e POSTGRES_DB=viz -p 5432:5432 postgres:16
cd backend
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/viz \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=viz \
./mvnw spring-boot:run
```

## Automated checks (merge gates)

```bash
cd backend && ./mvnw verify      # JUnit 5 unit, parsing, inference, Testcontainers integration
cd frontend && npm run typecheck # tsc --noEmit, strict
cd frontend && npm test          # Vitest
cd frontend && npm run e2e       # Playwright smoke of V1–V4; starts its own isolated backend (:8081, in-memory H2) and Vite (:5174)
```

Expected result: all green. The inference suite includes the **reference workspace** precision
assertion (≥ 80%, SC-003).

## Sample specs

The fixture set lives in `backend/src/test/resources/specs/reference-workspace/`:

| File | Format | Contains |
|------|--------|----------|
| `order-service.yaml` | OpenAPI 3.0 YAML | `GET /orders/{orderId}` → `customerId`, `items[].productId`, `status`, `id` |
| `customer-service.json` | OpenAPI 3.1 JSON | `GET /customers/{customerId}` → `email`, `status`, `id` |
| `catalog-service.yaml` | Swagger 2.0 YAML | `GET /products/{productId}`, with `$ref` to `definitions/Product` |
| `payment-service.json` | Swagger 2.0 JSON | `POST /payments` body `{ orderId, amount }` |
| `notification-service.yaml` | OpenAPI 3.0 YAML | `POST /notifications` body `{ email, message }` |
| `broken.yaml` | invalid | a `$ref` to a missing definition |

To serve them by URL for the URL scenarios:

```bash
# Serve a copy, so editing specs for the re-sync steps does not change the test fixtures
cp -r backend/src/test/resources/specs/reference-workspace /tmp/viz-specs
cd /tmp/viz-specs && python3 -m http.server 9000
```

The steps below that "edit" a spec mean editing the copy in `/tmp/viz-specs`. For services
registered by paste, paste the edited text through **Re-sync**.

---

## Validation scenarios

### V1: Register by paste and by URL (User Story 1)

1. Click **Add service → Paste**, paste `order-service.yaml`, and submit.
   **Expect**: an `order-service` node appears with GET/POST/… method badges and paths.
2. Click **Add service → URL**, enter `http://localhost:9000/catalog-service.yaml` (Swagger 2.0).
   **Expect**: a `catalog-service` node appears. Expanding `GET /products/{productId}` shows the
   `Product` fields fully expanded (no `$ref` names).
3. Paste `broken.yaml`.
   **Expect**: an error that names the missing reference location. No node is added.
4. Edit `catalog-service.yaml` on disk (add an endpoint), then click **Re-sync** on the node.
   **Expect**: the new endpoint appears. Re-syncing again without changes reports "unchanged".
5. Stop the `http.server`, then click **Re-sync**.
   **Expect**: an error toast. The node keeps its last good endpoints and shows a "sync failed"
   badge.
6. Paste `order-service.yaml` again.
   **Expect**: a prompt asking **Update existing** or **Register as new**. "Update" creates no
   duplicate.
7. Zoom out below about 45%.
   **Expect**: nodes collapse to name plus endpoint count.
8. Drag a node, reload the page.
   **Expect**: the position is kept. **Auto-arrange** re-lays out only when clicked.

API check:

```bash
curl -s localhost:8080/api/services | jq '.[].serviceKey'
```

### V2: Inferred, confirmed, deleted and manual connections (User Story 2)

Register every sample spec except `broken.yaml`.

1. **Expect** these dashed arrows:
   - order-service `GET /orders/{orderId}` → customer-service `GET /customers/{customerId}`
     (`customerId`);
   - order-service → catalog-service (`items[].productId` → `productId`);
   - customer-service → notification-service (`email`).
   - **No** arrow is drawn only because of `id`, `status` or `message`.
2. Click the order → customer arrow.
   **Expect**: a panel shows `customerId → customerId (PATH)` and a confidence of 0.9 or more,
   with **Confirm** and **Delete** buttons.
3. Click **Confirm**.
   **Expect**: the arrow turns solid.
4. Delete the customer → notification arrow.
   **Expect**: it disappears, and it is listed under **Dismissed connections**.
5. Drag from order-service's service handle to payment-service.
   **Expect**: a solid manual arrow.
6. Re-sync every service.
   **Expect**: the confirmed and manual arrows are still solid, and the dismissed one is still
   hidden (SC-004).
7. Edit `customer-service.json` to remove `GET /customers/{customerId}`, then re-sync.
   **Expect**: the confirmed arrow is shown amber with ⚠ "needs review". It is not deleted.
8. Restore the dismissed connection from the list.
   **Expect**: the dashed arrow returns.
9. Try to draw from a service to itself.
   **Expect**: the connection is refused.

API check:

```bash
curl -s localhost:8080/api/graph | jq '.edges[] | {id, type, c: .data.confidence, o: .data.orphaned}'
```

### V3: Blast radius and field search (User Story 3)

1. Click **order-service**.
   **Expect**: its direct downstream services (customer, catalog, payment) are outlined purple.
   It has no upstream services in this set; click customer-service to see order-service outlined
   blue as upstream. In both cases, unrelated services are dimmed, and the summary chip shows the counts.
   **Expect**: notification-service (two hops away) is dimmed.
2. Press **Esc**.
   **Expect**: the highlight clears.
3. Search for `customerid` (lower case).
   **Expect**: order-service (OUT) and customer-service (IN) are outlined yellow, the matching
   field rows are marked, and other services are dimmed.
4. Search for `doesNotExist`.
   **Expect**: a "No matches" message, and the canvas is unchanged.

### V4: Draft Scenario Mode (User Story 4)

1. Note the baseline: run `curl -s localhost:8080/api/graph | sha256sum`.
2. Toggle **Draft Scenario Mode**.
   **Expect**: a green banner and green canvas frame appear.
3. Add a proposed service `loyalty-service` with `POST /points` (input `customerId`).
   **Expect**: a dashed green border and a "PROPOSED" tag.
4. Add a proposed endpoint `GET /customers/{customerId}/tier` to customer-service.
   **Expect**: a green "+" row on that node only.
5. Draw order-service → loyalty-service.
   **Expect**: a **green dashed** arrow.
6. Click loyalty-service.
   **Expect**: the blast radius includes order-service as upstream.
7. Click **Save as** "Loyalty v1", then toggle draft mode off.
   **Expect**: the canvas is identical to the baseline. Re-run the step 1 command; it gives the
   **same hash** (SC-007).
8. Reopen "Loyalty v1" from **Scenarios**.
   **Expect**: all proposals are back.
9. Click **Export → JSON**, then **Scenarios → Import** with that file.
   **Expect**: "Loyalty v1 (imported)" reproduces the scenario.
10. Click **Export → Image**.
    **Expect**: a PNG with the proposed styling, the banner and the legend.
11. Open the app in a second tab, edit and save the same scenario in both tabs.
    **Expect**: the second save warns that the content changed (409) instead of overwriting.
12. Remove `GET /customers/{customerId}` from customer-service (as in V2 step 7) and reopen the
    scenario.
    **Expect**: proposed elements that reference it are flagged.

### V5: Screen-share readability

1. Turn on **Presentation mode** and **Full screen**, and share a 1080p screen in a meeting tool.
   **Expect**: service names, method badges, arrow styles and highlights are legible to
   viewers (FR-040).

### V6: Performance smoke (SC-002, SC-005, SC-006)

```bash
cd backend && ./mvnw -Dit.test=PerformanceSmokeIT verify   # seeds 50 services, about 500 edges
```

**Expect**: a 100-endpoint spec registers in under 10 s. In the browser, clicking a node
highlights in under 1 s, and pan and zoom stay smooth.
