# The connections document

The connections document describes your architecture in JSON:

- **microservices**;
- **connections**: each is one link from a microservice to another;
- **flows**: named, colored business processes, such as "Checkout", that run through several connections.

 You can upload it (**Import connections**), edit it in the app (**Edit connections**), and download it
(**Export ▾ → Connections document**). Drawing a connection on the canvas and adding it to the document are the same
thing: the canvas and the document always describe the same connections.

The JSON Schema is in
[`specs/003-declared-connections/contracts/connections-document.schema.json`](../specs/003-declared-connections/contracts/connections-document.schema.json).
A running backend also serves it at `/connections-document.schema.json`, so editors such as VS Code can validate
your file with `"$schema"` tooling.

## Example

```json
{
  "formatVersion": 2,
  "microservices": [
    { "name": "Billing", "description": "Invoices and payments", "team": "Payments", "repository": "https://github.com/acme/billing", "docs": "https://docs.acme.dev/billing" },
    { "name": "Orders", "description": "Order lifecycle" }
  ],
  "flows": [
    { "name": "Checkout", "color": "blue" },
    { "name": "Payments", "color": "orange" }
  ],
  "connections": [
    { "name": "Create invoice", "source": "Orders", "target": "Billing", "kind": "request", "endpoint": "POST /invoices", "flow": "Checkout", "step": 1 },
    { "name": "Order placed", "source": "Orders", "target": "Billing", "kind": "event", "flow": "Checkout", "step": 2, "description": "Published on checkout" },
    { "name": "Invoice paid", "source": "Billing", "target": "Orders", "kind": "event", "flow": "Payments" }
  ]
}
```

## Rules

| Property | Required | Rules |
|---|---|---|
| `formatVersion` | yes | Must be `2`. Version `1` files, written before connections and flows were renamed, still import: they call connections `flows`, flows `groups`, and a connection's flow `group`. |
| `connections` | yes | A list; may be empty. At most 5,000 entries. |
| `connections[].name` | yes | 1–120 characters. |
| `connections[].source`, `connections[].target` | yes | Microservice names, 1–120 characters. They must differ. |
| `connections[].kind` | no | `"request"` (a synchronous call) or `"event"` (an asynchronous message). Without a kind, a connection is drawn dotted. |
| `connections[].description` | no | Up to 2,000 characters. |
| `connections[].endpoint` | no | One endpoint of the **target** microservice's API, as `METHOD /path`, for example `POST /invoices`. Shown explicitly on the diagram as a badge (method and path) near the arrowhead. If the target has a spec card without that endpoint (compared like `/orders/{id}` = `/orders/{orderId}`), the connection is flagged ⚠ and kept as is. |
| `connections[].flow` | no | The name of a flow listed in `flows`. The connection is drawn in that flow's color. |
| `connections[].step` | no | The connection's step in its flow: a whole number from 1. Connections with the same step happen in parallel. Needs `flow`. Without a step, a connection that joins a flow comes after the flow's last step; one already in the flow keeps its step. |
| `microservices` | no | A list of microservices, each with `name` (required), and optionally `description`, `team`, `repository` and `docs`. At most 2,000 entries. |
| `microservices[].team` | no | The owning team, up to 120 characters. |
| `microservices[].repository`, `microservices[].docs` | no | Links to the repository and the documentation: `http` or `https` URLs. |
| `flows` | no | A list of flows, each with `name` (required, unique) and `color`: one of `blue`, `purple`, `orange`, `pink`, `amber`, `brown`, `cyan`, `slate`. Green and red are reserved for proposed and removed elements in Draft Scenario Mode. |

- **Names compare loosely.** Case, surrounding spaces and repeated inner spaces are ignored, so `Order  Service` and `order service` are the same name.
- **Several connections between two microservices are fine,** in either direction. Two connections with the same source and target must have different names.
- **Unknown properties are errors.** A typo such as `"lable"` is reported rather than silently ignored.
- The whole document must be at most 2 MB.

## What applying a document does

- **The document replaces all connections.** Connections that are not in it are removed after you confirm the preview, which lists every connection that will be added, changed or removed.
- **Microservices named in the document but missing on the canvas are created,** without a spec. A listed `description`, `team`, `repository` or `docs` updates an existing microservice; leaving one out keeps the current value, and `null` clears it.
- **Microservices and flows are never deleted by a document.** Delete them on the canvas or in the Flows panel. Flows named in the document are created if missing, and their color is updated.
- **A document with any error changes nothing.** Every error is listed with its position, for example `connections[3].target is missing (line 18:5)`.
- **Applying the same document twice changes nothing,** and so does exporting a document and importing it back unchanged. The one exception is **dangling connections**: connections whose microservice was deleted can't be written in the document. The export warns about them, and re-importing such an export lists them under Removed.

## Flows

Flows color related connections, so a business process that spans several microservices is easy to follow.
Manage them from the legend (**+ Flows** / **Edit flows**), assign a connection to a flow when you draw
it or in its details panel, and click a flow in the legend to highlight its connections.

Number a flow's connections as **steps** (in the connection's details panel, or with ▲/▼ under **Steps** in the
Flows panel) and press **▶** next to the flow in the legend to walk through it one step at a time: ← and →
move, Esc ends.

## In Draft Scenario Mode

**Export ▾ → Connections document (with scenario applied)** downloads the baseline document with the scenario applied:
- proposed microservices and connections are added;
- connections and microservices proposed for removal are left out;
- proposed connection changes are applied.

The current connections on the canvas don't change.
