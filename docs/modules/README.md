# Backend modules

The backend is a [Spring Modulith](https://docs.spring.io/spring-modulith/reference/) modular monolith. Each module
uses ports and adapters (hexagonal architecture) inside. This folder is **generated from the code**, except this
README. The design behind it is in [`specs/005-modular-backend/`](../../specs/005-modular-backend/), in particular
[`contracts/module-apis.md`](../../specs/005-modular-backend/contracts/module-apis.md).

## Regenerating

```bash
cd backend && ./mvnw test -Dtest=ModularityTest
```

`ModularityTest.writesDocumentation()` rewrites every `.puml` and `.adoc` file here. It also runs in the normal
`./mvnw verify`, and the output is deterministic, so a diff in this folder means a module dependency or a
module's public API changed. Commit it along with the code change.

| File | Content |
|---|---|
| `components.puml` | All modules and their dependencies (PlantUML, C4 style) |
| `module-<name>.puml` | One module and the modules it uses |
| `module-<name>.adoc` | The module "canvas": base package, Spring components, the beans it uses from other modules |
| `all-docs.adoc` | Includes all of the above in one AsciiDoc document |

Render the `.puml` files with any PlantUML viewer (for example the IDE plugin, or `plantuml *.puml`).

## Modules

Arrows read "may depend on". `ModularityTest.verifiesModularStructure()` fails the build on any other dependency,
on a cycle, or when a module reaches into another module's internals.

| Module | What it owns | May depend on |
|---|---|---|
| `shared` (open) | Errors, the API exception handler, `PatchBody`, `XYPosition`, `Names`, configuration | — |
| `workspace` | The workspace revision (optimistic concurrency for the connections document) | shared |
| `note` | Sticky notes on the canvas | shared |
| `card` | Spec cards: fetching and parsing OpenAPI/Swagger specs | shared |
| `microservice` | Microservices, ownership, their 1:1 spec link | shared, workspace, card, `card :: spi` |
| `connection` | Connections, flows, the connections document | shared, workspace, microservice, `microservice :: spi`, `card :: model` |
| `layout` | Saved node positions and collapsed cards | shared, microservice, card |
| `canvas` | Read-only composition of `GET /api/canvas`; the baseline index | shared, workspace, card, `card :: model`, microservice, connection, layout, note |
| `scenario` | Draft scenarios over the baseline (never changes it) | shared, canvas, `card :: model`, connection, note |

## Inside a module

```text
<module>/
  package-info.java        @ApplicationModule(allowedDependencies = …)
  <Module>Api, *View, enums  public API: framework-free (no JPA, Spring, Jackson, Swagger)
  spi/                     ports another module implements (card, microservice)
  model/                   read-only value types other modules may use (card)
  domain/                  plain model and rules: no JPA, Hibernate, Spring or Jackson
  application/             use-case services (package-private), implementing port.in and the <Module>Api
    port/in/               <Module>UseCases: what the inbound adapters may call
    port/out/              what the module needs: stores, fetcher/parser, other modules
  adapter/in/web/          controllers and REST DTOs (the @Schema names of the published API)
  adapter/in/<module>/     implementations of another module's spi
  adapter/out/persistence/ *Entity, *Mapper, Spring Data repository, *StoreAdapter
  adapter/out/<other>/     e.g. card's spec fetcher and parser, connection's document codec, other modules' APIs
```

`ModularityTest` also enforces this layout. It runs the jMolecules hexagonal rules (ports and application never
depend on adapters), checks that `domain/` and the root API are framework-free, and checks that only
`adapter/out/persistence` touches entities and repositories.

## The two inverted dependencies

Two pairs of modules used to depend on each other. In both, the lower module now declares an **SPI** (a port it
needs) and the higher module implements it, so the compile-time dependencies only point one way:

| SPI | Declared by | Implemented by | Used for |
|---|---|---|---|
| `microservice.spi.ConnectionEndsPort` | microservice | `connection.adapter.in.microservice.ConnectionEndsAdapter` → `ConnectionEndsService` | Deleting a microservice leaves its connection ends dangling (names kept); renaming renames them; creating one reattaches dangling ends with its name |
| `card.spi.CardLinkPort` | card | `microservice.adapter.in.card.CardLinkAdapter` → `CardLinkService` | Showing which microservice a card is linked to; unlinking it before the card is deleted |

The generated diagrams show only the compile-time direction (connection → microservice, microservice → card).
At runtime the calls also go the other way through these ports.

**Why synchronous calls and not events** (research R4, R5): these calls must run inside the caller's
transaction and in a fixed order. For example, connection ends are detached and flushed *before* the microservice
row is deleted, and the card is unlinked *before* the card row is deleted. Their results also feed the response
(the dangling and reattached connection ids). Spring Modulith's event listeners run after commit, which would
change both the atomicity and the responses.

**Why a separate small service behind each SPI adapter**: the microservice service calls card, and card calls
back into microservice through `CardLinkPort` (the same holds for microservice and connection). If the
callback went into the module's main service, Spring would find a circular bean dependency at startup. Each
adapter therefore delegates to a service that depends only on its own module's store.
