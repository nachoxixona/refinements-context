# Microservice Architecture & Scenario Visualizer

A canvas for refinement meetings that shows your microservice architecture:

- **Spec cards**: import an OpenAPI/Swagger spec by pasting it or giving its URL, and it becomes a card
  showing its endpoints and request/response fields. Importing never creates connections.
- **Microservices**: named nodes, with or without a spec. Link a spec card to a microservice (drop the card
  on it); the card docks below it and can be collapsed to an "API" badge.
- **Connections**: the links between microservices, declared by the team and never inferred. Each has a name,
  an optional kind (`request` drawn solid, `event` dashed), an optional description and optionally the
  target's endpoint it uses (shown as a badge such as `POST /invoices` near the arrowhead). Two
  microservices can have any number of connections.
- **Flows**: named, colored business processes, such as "Checkout", that run through several connections.
  Assign connections to a flow and click it in the legend to follow it across the architecture. Number its
  connections as steps and press ▶ to walk through the flow one step at a time in a meeting.
- **Ownership**: record each microservice's team and its repository and documentation links; filter the canvas
  by team.
- **Notes**: add sticky notes (note, decision, question) to the canvas to capture what a meeting decided.
- **Undo/redo**: Ctrl+Z / Ctrl+Shift+Z (or the ↶ ↷ buttons) undo and redo canvas edits made in this session.
- Draw connections on the canvas, or manage connections and flows in the **connections document**, a JSON
  file you can import, edit in the app and export ([format](docs/connections-document.md)).
- **Search and highlight**: search fields and endpoints across all cards; click a microservice to highlight
  its direct upstream and downstream neighbours.
- **Draft Scenario Mode**: sketch proposed microservices, links, connections, endpoints and fields, and propose
  removing or changing existing connections, without touching the baseline. Save, export as JSON or PNG.

Docs:

- Current feature: [`specs/003-declared-connections/`](specs/003-declared-connections/) (spec, plan, data
  model, contracts, quickstart)
- Previous feature (inferred connections, superseded): [`specs/002-architecture-visualizer/`](specs/002-architecture-visualizer/)
- Backend architecture: [`docs/modules/`](docs/modules/README.md) (modules, their dependencies and inner
  layout; diagrams generated from the code)
- Project principles: [`.specify/memory/constitution.md`](.specify/memory/constitution.md) (v2.1.0)

| Part | Stack |
|------|-------|
| `backend/` | Java 21, Spring Boot 3.5, swagger-parser 2.1, Flyway, PostgreSQL 16 |
| `frontend/` | React 18, TypeScript (strict), Vite, @xyflow/react 12, elkjs, CodeMirror 6, TanStack Query, Zustand |

## Prerequisites

- JDK 21
- Node.js 22 LTS and npm
- Docker, for the database, the Testcontainers and Playwright tests, and the all-in-one image

## Run with Docker (whole app)

```bash
docker compose up -d --build
```

Open http://localhost:8080. This builds one image (the frontend is built and served by the backend) and
starts it with a PostgreSQL 16 container; data lives in the `db-data` volume. Change the host ports with
`APP_PORT=9090 DB_PORT=5434 docker compose up -d --build`.

## Run locally (development)

```bash
# PostgreSQL on :5433 (user, password and database: visualizer)
docker compose up -d db

# Backend on :8080
cd backend && ./mvnw spring-boot:run

# Frontend on :5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173. The backend's API docs are at http://localhost:8080/swagger-ui.html.
Point the backend at another database with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`.

The schema is a single Flyway script, `backend/src/main/resources/db/migration/V1__schema.sql`. Add a
`V2__….sql` next to it for future schema changes.

## Tests and merge gates

```bash
cd backend  && ./mvnw verify        # unit + Testcontainers integration tests Spotless check
cd frontend && npm run typecheck    # tsc --noEmit, strict
cd frontend && npm run lint         # ESLint
cd frontend && npm test             # Vitest
cd frontend && npm run e2e          # Playwright; starts a throwaway PostgreSQL container, a backend (:8081) and Vite (:5174)
```

The API contract lives in `specs/004-meeting-tools/contracts/openapi.yaml`. After changing it, run
`npm run gen:api` in `frontend/` to regenerate the TypeScript types; `ContractConformanceIT` fails the
backend build if the runtime API drifts from it.

For end-to-end validation scenarios, see
[`specs/003-declared-connections/quickstart.md`](specs/003-declared-connections/quickstart.md).
